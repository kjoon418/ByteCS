package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserSettings
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import java.time.LocalDate
import java.util.Optional
import kotlin.random.Random

/**
 * 연결 문제 하드 게이트(계획 §3.2 · DI12)를 검증한다.
 * 게이트는 새 개념 후보 필터로만 작용한다 — **연결 문제로 지정된(integration=true)** 문제는 구성 개념을 **모두** 학습한
 * (숙련도 행이 존재하는, 레벨 무관) 사용자에게만 후보가 된다. **미지정 문제는 개념 수와 무관하게 통과**하고(DI12: 판별을
 * 태깅 수에서 명시 속성으로 이관), 복습 편입·D8 반복 폴백도 게이트 밖이다. 지정 여부·개념 태깅은 저장소 조회 결과라 stub한다.
 */
class SessionCreatorConnectionGateTest {

    private val sessionRepository = mock(SessionRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val reviewService = mock(ReviewService::class.java)

    private val learningHistory = LearningHistory(sessionRepository)
    private val difficultyWeightedShuffler = DifficultyWeightedShuffler()

    private companion object {
        const val USER_ID = 1L
        const val SEED = 42L
        val TODAY: LocalDate = LocalDate.of(2026, 7, 22)
    }

    @Test
    fun `지정된 연결 문제는 구성 개념 중 하나라도 미학습이면 새 개념 후보에서 제외된다`() {
        // 문제 10 = 지정된 연결 문제(개념 201·202). 사용자는 201만 학습 → 202 미학습이라 게이트에 걸린다.
        val assignedIds = assign(
            size = 3, all = listOf(1L, 2L, 10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = setOf(201L),
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L)
        assertThat(assignedIds).doesNotContain(10L)
    }

    @Test
    fun `지정된 연결 문제는 전 구성 개념을 학습했으면 후보에 포함된다(레벨 무관 - 행 존재면 충분)`() {
        // 201·202를 모두 학습(숙련도 행 존재 — 레벨이 0이어도 게이트는 통과). 지정 연결 문제 10이 후보가 된다.
        val assignedIds = assign(
            size = 3, all = listOf(1L, 2L, 10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = setOf(201L, 202L),
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L, 10L)
    }

    @Test
    fun `지정되지 않은 다개념 문제는 미학습이어도 게이트 영향을 받지 않는다`() {
        // 문제 10은 개념이 2개지만 연결 문제로 지정되지 않았다(integration=false → 게이트 쿼리 결과에 없음).
        // 보유 개념이 하나도 없어도 그대로 후보가 된다(DI12: 다개념 태깅 자체는 게이트와 무관).
        val assignedIds = assign(
            size = 3, all = listOf(1L, 2L, 10L),
            flaggedConcepts = emptyMap(),
            mastered = emptySet(),
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L, 10L)
    }

    @Test
    fun `단일 개념 문제는 학습 이력이 없어도 게이트 영향을 받지 않는다`() {
        // 지정된 연결 문제가 후보에 하나도 없으면(모두 미지정) 게이트가 걸릴 것이 없어 전부 통과한다.
        val assignedIds = assign(
            size = 3, all = listOf(1L, 2L),
            flaggedConcepts = emptyMap(),
            mastered = emptySet(),
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `복습으로 편입된 지정 연결 문제는 게이트와 무관하게 배정된다`() {
        // 지정 연결 문제 10이 복습으로 도래. 구성 개념 미학습이어도 복습 편입은 새 개념 필터 밖이라 그대로 배정된다.
        val assignedIds = assign(
            size = 2, all = listOf(10L, 1L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = emptySet(),
            solved = listOf(10L), reviews = listOf(10L),
        )

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds.first()).isEqualTo(10L)
        assertThat(assignedIds).contains(1L)
    }

    @Test
    fun `반복 폴백(D8)은 지정 연결 문제라도 게이트를 적용하지 않는다`() {
        // 안 푼 문제가 없어(전부 풀었음) D8 반복 폴백 → 전체 풀 재출제. 지정 연결 문제 10이 미학습이어도 게이트 밖이다.
        val assignedIds = assign(
            size = 2, all = listOf(10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = emptySet(),
            solved = listOf(10L), reviews = emptyList(),
        )

        assertThat(assignedIds).containsExactly(10L)
    }

    @Test
    fun `게이트로 후보가 줄어도 단일 개념 문제로 세션이 성립한다`() {
        // 지정 연결 문제 10만 게이트에 걸려 빠지고, 남은 단일 개념 1·2로 세션이 온전히 구성된다(빈 세션 아님).
        val assignedIds = assign(
            size = 3, all = listOf(1L, 2L, 10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = emptySet(),
        )

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L)
        assertThat(assignedIds).doesNotContain(10L)
    }

    @Test
    fun `안 푼 문제가 전부 잠긴 지정 연결 문제면 반복 폴백으로 세션을 만든다(막다른 길 없음)`() {
        // 안 푼 10(지정 연결·미학습)만 남아 게이트로 전부 빠지고 복습도 없다 → 404 없이 D8 반복 폴백으로 세션이 성립한다.
        val assignedIds = assign(
            size = 2, all = listOf(1L, 10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = emptySet(),
            solved = listOf(1L),
        )

        assertThat(assignedIds).isNotEmpty()
        assertThat(assignedIds).allMatch { it in setOf(1L, 10L) }
    }

    @Test
    fun `신규 사용자에게 콘텐츠가 전부 잠긴 지정 연결 문제뿐이어도 세션을 만든다(게이트 완화 수용)`() {
        // solved 0 + 전 콘텐츠가 잠긴 지정 연결 문제(병리적 구성) → 게이트가 완화돼 반복 폴백으로 세션 성립(404 없음).
        val assignedIds = assign(
            size = 2, all = listOf(10L, 11L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L), 11L to listOf(301L, 302L)),
            mastered = emptySet(),
        )

        assertThat(assignedIds).isNotEmpty()
        assertThat(assignedIds).containsExactlyInAnyOrder(10L, 11L)
    }

    @Test
    fun `선호 난이도가 설정돼도 게이트가 먼저 적용된 뒤 가중 배정된다`() {
        // 필터 → 가중 순서: 지정 연결 문제 10은 게이트에서 먼저 빠지고, 남은 1·2·3만 난이도 가중으로 정렬된다.
        // 분량이 후보 수 이상이라 가중은 후보를 배제하지 않으므로, 10의 부재는 게이트가 가중 앞에서 작동한 증거다.
        val difficulties = mapOf(1L to Difficulty.EASY, 2L to Difficulty.MEDIUM, 3L to Difficulty.HARD)
        val assignedIds = assign(
            size = 4, all = listOf(1L, 2L, 3L, 10L),
            flaggedConcepts = mapOf(10L to listOf(201L, 202L)),
            mastered = emptySet(),
            preferred = Difficulty.EASY, difficulties = difficulties,
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L, 3L)
        assertThat(assignedIds).doesNotContain(10L)
        // 가중 경로가 실제로 탔음을 확인(균등 경로였다면 호출되지 않는다).
        verify(problemRepository).findApprovedDifficultiesByIdIn(anyList())
    }

    /**
     * 주어진 학습 상태·지정 연결 문제 개념 태깅·보유 개념을 stub하고 시드 고정 Random으로 create를 구동해,
     * 배정된 본 문제 id를 순서대로 돌려준다.
     * [flaggedConcepts]는 **integration=true로 지정된 문제만** 담는다(게이트 쿼리가 지정 문제만 돌려주므로) —
     * 미지정 문제는 여기에 없고, 게이트는 그런 문제를 통과시킨다.
     */
    private fun assign(
        size: Int,
        all: List<Long>,
        flaggedConcepts: Map<Long, List<Long>>,
        mastered: Set<Long>,
        solved: List<Long> = emptyList(),
        reviews: List<Long> = emptyList(),
        preferred: Difficulty? = null,
        difficulties: Map<Long, Difficulty?> = emptyMap(),
    ): List<Long> {
        val user = User.createGuest().apply {
            updateSettings(UserSettings(size))
            preferred?.let { updatePreferredDifficulty(it) }
        }

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user))
        given(problemRepository.findApprovedIdsOrderByIdAsc()).willReturn(all)
        given(sessionRepository.findAssignedProblemIds(user.id)).willReturn(solved)
        given(sessionRepository.findSolvedProblemIds(user.id)).willReturn(solved)
        given(reviewService.selectDueReviewProblemIds(user.id, TODAY, solved.toSet(), all.toSet()))
            .willReturn(reviews)
        given(reviewService.findMasteredConceptIds(user.id)).willReturn(mastered)
        given(problemRepository.findConceptIdsOfIntegrationProblems(anyList())).willReturn(conceptViews(flaggedConcepts))
        given(sessionRepository.save(any(Session::class.java))).willAnswer { it.getArgument(0) }
        if (preferred != null) {
            given(problemRepository.findApprovedDifficultiesByIdIn(anyList()))
                .willReturn(difficultyViews(difficulties))
        }

        val creator = SessionCreator(
            sessionRepository, problemRepository, userRepository, reviewService, learningHistory,
            difficultyWeightedShuffler, Random(SEED),
        )
        return creator.create(USER_ID, TODAY).items.map { it.problemId }
    }

    /** 지정 연결 문제의 id→개념 id 목록 맵을 (문제 id, 개념 id) 쌍 프로젝션 뷰 목록으로 펼친다(게이트가 지정 문제의 개념을 읽는 입력). */
    private fun conceptViews(flaggedConcepts: Map<Long, List<Long>>): List<ProblemRepository.ProblemConceptView> =
        flaggedConcepts.flatMap { (problemId, conceptIds) ->
            conceptIds.map { conceptId ->
                object : ProblemRepository.ProblemConceptView {
                    override val problemId = problemId
                    override val conceptId = conceptId
                }
            }
        }

    /** id→난이도 맵을 프로젝션 뷰 목록으로 바꾼다(가중 셔플러가 후보별 난이도를 읽는 입력). */
    private fun difficultyViews(difficulties: Map<Long, Difficulty?>): List<ProblemRepository.ProblemDifficultyView> =
        difficulties.map { (problemId, difficulty) ->
            object : ProblemRepository.ProblemDifficultyView {
                override val id = problemId
                override val difficulty = difficulty
            }
        }
}
