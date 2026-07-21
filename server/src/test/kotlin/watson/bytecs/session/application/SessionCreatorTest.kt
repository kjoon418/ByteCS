package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
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
 * 세션 편입(§3 340행) 알고리즘을 검증한다.
 * 복습 문제 선정 자체는 [ReviewService]에 위임하므로 여기선 stub하고, 병합(복습 우선·중복 제거·분량 절단·폴백)만 검증한다.
 * 새 개념 문제는 무작위로 배정되므로(QA #6), 시드 고정 [Random]을 주입해 셔플 결과를 결정적으로 검증한다.
 */
class SessionCreatorTest {

    private val sessionRepository = mock(SessionRepository::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val reviewService = mock(ReviewService::class.java)

    // 실제 LearningHistory로 세션 이력 경로를 그대로 태운다(D6·D9 일원화 이후 세션 단독 출처).
    private val learningHistory = LearningHistory(sessionRepository)

    // 가중 셔플러는 실제 구현을 쓴다(순서화 규칙은 자체 단위 테스트가 검증하고, 여기선 분기·후보 보존을 본다).
    private val difficultyWeightedShuffler = DifficultyWeightedShuffler()

    private companion object {
        const val USER_ID = 1L
        const val SEED = 42L
        val TODAY: LocalDate = LocalDate.of(2026, 7, 14)
    }

    @Test
    fun `복습이 없으면 아직 안 푼 문제만 분량만큼 배정한다`() {
        val assignedIds = assign(size = 3, all = listOf(1L, 2L, 3L, 4L, 5L), solved = listOf(2L, 4L), reviews = emptyList())

        // 이미 푼 2·4는 빠지고, 안 푼 1·3·5만 무작위 순서로 세 칸을 채운다.
        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 3L, 5L)
        assertThat(assignedIds).doesNotContain(2L, 4L)
    }

    @Test
    fun `안 푼 문제가 분량보다 많으면 무작위로 분량만큼만 뽑는다`() {
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList())

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds).doesNotHaveDuplicates()
        assertThat(assignedIds).allMatch { it in setOf(1L, 2L, 3L, 4L, 5L) }
    }

    @Test
    fun `도래한 복습 문제를 먼저 배정하고 남은 칸을 새 개념 무작위로 채운다`() {
        // 복습 30번이 항상 먼저, 남은 한 칸은 아직 안 푼 문제 중 무작위 하나.
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L, 4L, 30L), solved = listOf(30L), reviews = listOf(30L))

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds.first()).isEqualTo(30L)
        assertThat(assignedIds[1]).isIn(1L, 2L, 3L, 4L)
    }

    @Test
    fun `복습이 세션 분량을 넘으면 도래 순으로 자르고 새 개념은 들어가지 않는다`() {
        // 상한 없음: 도래 복습이 세션을 다 채울 수 있다. 복습은 셔플하지 않으므로 도래 순 우선으로 잘린다.
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L, 40L, 50L, 60L), solved = listOf(40L, 50L, 60L), reviews = listOf(40L, 50L, 60L))

        assertThat(assignedIds).containsExactly(40L, 50L)
    }

    @Test
    fun `복습과 새 개념이 겹치면 중복을 선착순으로 제거한다`() {
        // 복습 후보 2번이 아직 안 푼 목록에도 있으면(안 낸 유도형 예외 등), 복습으로 한 번만 편입된다.
        val assignedIds = assign(size = 3, all = listOf(1L, 2L, 3L), solved = emptyList(), reviews = listOf(2L))

        // 복습 2번이 맨 앞에 고정되고, 남은 칸은 새 개념 무작위(1·3)로 중복 없이 채워진다.
        assertThat(assignedIds.first()).isEqualTo(2L)
        assertThat(assignedIds).containsExactlyInAnyOrder(2L, 1L, 3L)
    }

    @Test
    fun `새로 풀 문제가 없으면 전체 풀에서 무작위로 폴백 배정한다`() {
        val assignedIds = assign(size = 2, all = listOf(1L, 2L, 3L), solved = listOf(1L, 2L, 3L), reviews = emptyList())

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds).doesNotHaveDuplicates()
        assertThat(assignedIds).allMatch { it in setOf(1L, 2L, 3L) }
    }

    @Test
    fun `같은 학습 상태에 같은 시드면 항상 같은 세션이 만들어진다`() {
        val first = assign(size = 3, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList(), seed = SEED)
        val second = assign(size = 3, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList(), seed = SEED)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `같은 학습 상태라도 시드가 다르면 다른 세션이 나올 수 있다`() {
        // 여러 시드로 배정한 세션이 모두 동일하지는 않다 = 무작위 배정이 실제로 일어난다(QA #6).
        val sessions = (1L..30L).map { seed ->
            assign(size = 3, all = listOf(1L, 2L, 3L, 4L, 5L, 6L), solved = emptyList(), reviews = emptyList(), seed = seed)
        }

        assertThat(sessions.distinct().size).isGreaterThan(1)
    }

    // ── 선호 난이도 가중 출제(Stage 2) ────────────────────────────────────

    @Test
    fun `선호 난이도가 설정되면 난이도 조회로 가중 배정하고 안 푼 후보를 완전히 배제하지 않는다`() {
        // 안 푼 5문제(난이도 혼재), 선호 EASY. 분량이 후보 수 이상이면 모든 후보가 포함돼야 한다(완전 배제 없음).
        val difficulties = mapOf(
            1L to Difficulty.EASY, 2L to Difficulty.MEDIUM, 3L to Difficulty.HARD,
            4L to Difficulty.EASY, 5L to Difficulty.HARD,
        )
        val assignedIds = assign(
            size = 5, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList(),
            preferred = Difficulty.EASY, difficulties = difficulties,
        )

        assertThat(assignedIds).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L)
        // 가중 경로는 후보 난이도를 조회한다(균등 경로였다면 호출되지 않는다).
        verify(problemRepository).findApprovedDifficultiesByIdIn(anyList())
    }

    @Test
    fun `선호 난이도 미설정이면 난이도 조회 없이 기존 균등 경로로 배정한다`() {
        // preferred=null(기본) — 회귀 0: 난이도 조회 자체가 일어나지 않아야 한다.
        assign(size = 3, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList())

        verify(problemRepository, never()).findApprovedDifficultiesByIdIn(anyList())
    }

    @Test
    fun `안 푼 문제가 없으면 선호가 있어도 D8 반복 폴백은 난이도 가중을 적용하지 않는다`() {
        // 전부 이미 푼 상태 → D8 반복 폴백. 선호가 설정돼 있어도 난이도 조회 없이 균등 폴백이어야 한다.
        val assignedIds = assign(
            size = 2, all = listOf(1L, 2L, 3L), solved = listOf(1L, 2L, 3L), reviews = emptyList(),
            preferred = Difficulty.HARD,
            difficulties = mapOf(1L to Difficulty.HARD, 2L to Difficulty.EASY, 3L to Difficulty.MEDIUM),
        )

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds).allMatch { it in setOf(1L, 2L, 3L) }
        verify(problemRepository, never()).findApprovedDifficultiesByIdIn(anyList())
    }

    @Test
    fun `복습 편입은 선호 난이도와 무관하게 도래 순으로 먼저 배치된다`() {
        // 선호 EASY라도 복습(30)은 가중과 무관하게 맨 앞에 고정되고, 남은 칸만 새 개념 가중으로 채운다.
        val assignedIds = assign(
            size = 2, all = listOf(1L, 2L, 3L, 4L, 30L), solved = listOf(30L), reviews = listOf(30L),
            preferred = Difficulty.EASY,
            difficulties = mapOf(1L to Difficulty.EASY, 2L to Difficulty.MEDIUM, 3L to Difficulty.HARD, 4L to Difficulty.EASY),
        )

        assertThat(assignedIds).hasSize(2)
        assertThat(assignedIds.first()).isEqualTo(30L)
        assertThat(assignedIds[1]).isIn(1L, 2L, 3L, 4L)
    }

    @Test
    fun `선호 난이도를 미설정으로 되돌리면 난이도 조회 없이 균등 경로로 배정한다`() {
        // 선호를 EASY로 뒀다가 되돌린 사용자 → 미설정과 동일하게 난이도 조회 자체가 일어나지 않아야 한다.
        assign(
            size = 3, all = listOf(1L, 2L, 3L, 4L, 5L), solved = emptyList(), reviews = emptyList(),
            preferred = Difficulty.EASY, resetAfter = true,
            difficulties = mapOf(1L to Difficulty.EASY, 2L to Difficulty.MEDIUM, 3L to Difficulty.HARD),
        )

        verify(problemRepository, never()).findApprovedDifficultiesByIdIn(anyList())
    }

    /** 주어진 학습 상태를 stub하고 시드 고정 Random으로 create를 구동해, 배정된 본 문제 id를 순서대로 돌려준다. */
    private fun assign(
        size: Int,
        all: List<Long>,
        solved: List<Long>,
        reviews: List<Long>,
        seed: Long = SEED,
        preferred: Difficulty? = null,
        resetAfter: Boolean = false,
        difficulties: Map<Long, Difficulty?> = emptyMap(),
    ): List<Long> {
        val user = User.createGuest().apply {
            updateSettings(UserSettings(size))
            preferred?.let { updatePreferredDifficulty(it) }
            if (resetAfter) resetPreferredDifficulty()
        }

        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user))
        given(problemRepository.findApprovedIdsOrderByIdAsc()).willReturn(all)
        // 배정·풀이 이력은 [LearningHistory](세션 단독 출처)를 본다.
        given(sessionRepository.findAssignedProblemIds(user.id)).willReturn(solved)
        given(sessionRepository.findSolvedProblemIds(user.id)).willReturn(solved)
        given(reviewService.selectDueReviewProblemIds(user.id, TODAY, solved.toSet(), all.toSet()))
            .willReturn(reviews)
        given(sessionRepository.save(org.mockito.ArgumentMatchers.any(Session::class.java)))
            .willAnswer { it.getArgument(0) }
        // 가중 경로에서만 후보 난이도를 조회한다. 균등·D8·되돌리기 경로에서는 호출되지 않으므로 스텁도 필요 없다.
        if (preferred != null && !resetAfter) {
            given(problemRepository.findApprovedDifficultiesByIdIn(anyList()))
                .willReturn(difficultyViews(difficulties))
        }

        val creator = SessionCreator(
            sessionRepository, problemRepository, userRepository, reviewService, learningHistory,
            difficultyWeightedShuffler, Random(seed),
        )
        val session = creator.create(USER_ID, TODAY)
        return session.items.map { it.problemId }
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
