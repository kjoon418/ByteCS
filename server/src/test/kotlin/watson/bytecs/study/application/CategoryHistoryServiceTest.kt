package watson.bytecs.study.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import watson.bytecs.study.application.dto.CategoryHistoryResponse

/**
 * 카테고리별 학습 이력 서비스 슬라이스 테스트.
 * 그룹핑(대표 분류 기준·8분류 전체·빈 카테고리 포함)이 이 서비스의 핵심 책임이다.
 * 매퍼([CategoryHistoryResponseMapper])는 의존성 없는 순수 변환기라 실제 인스턴스를 그대로 써서(모킹하지 않고)
 * 최종 응답을 직접 검증한다 — Kotlin non-null 파라미터에 Mockito any()/capture()를 섞으면 널 검사 예외가 나는 문제도 피한다.
 */
class CategoryHistoryServiceTest {

    private val learningHistory = mock(LearningHistory::class.java)
    private val problemRepository = mock(ProblemRepository::class.java)
    private val sessionRepository = mock(SessionRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)

    private val service = CategoryHistoryService(
        learningHistory,
        problemRepository,
        sessionRepository,
        userRepository,
        CategoryHistoryResponseMapper(),
    )

    private companion object {
        const val USER_ID = 1L
    }

    @Test
    fun `존재하지 않는 사용자를 조회하면 예외를 던진다`() {
        given(userRepository.existsById(USER_ID)).willReturn(false)

        assertThatThrownBy { service.findByCategory(USER_ID) }
            .isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun `8개 카테고리 전체를 선언 순서대로 반환하며 문제가 없으면 빈 목록이다`() {
        given(userRepository.existsById(USER_ID)).willReturn(true)
        given(learningHistory.findSolvedProblemIds(USER_ID)).willReturn(emptySet())
        given(problemRepository.findAllById(emptySet<Long>())).willReturn(emptyList())
        given(sessionRepository.findSolvedItemAnswers(USER_ID)).willReturn(emptyList())

        val result = service.findByCategory(USER_ID)

        assertThat(result.map { it.category }).containsExactlyElementsOf(ProblemCategory.entries.map { it.name })
        assertThat(result).allSatisfy { group: CategoryHistoryResponse -> assertThat(group.items).isEmpty() }
    }

    @Test
    fun `대표 분류가 같은 문제끼리 묶고 문제가 없는 카테고리는 빈 목록이다`() {
        val dataStructureProblem = problemOf("개념1", ProblemCategory.DATA_STRUCTURE)
        val networkProblem = problemOf("개념2", ProblemCategory.NETWORK)

        given(userRepository.existsById(USER_ID)).willReturn(true)
        given(learningHistory.findSolvedProblemIds(USER_ID)).willReturn(setOf(10L, 20L))
        given(problemRepository.findAllById(setOf(10L, 20L))).willReturn(listOf(dataStructureProblem, networkProblem))
        given(sessionRepository.findSolvedItemAnswers(USER_ID)).willReturn(emptyList())

        val result = service.findByCategory(USER_ID)

        assertThat(result.first { it.category == "DATA_STRUCTURE" }.items)
            .extracting<String> { it.question }.containsExactly(dataStructureProblem.questionText)
        assertThat(result.first { it.category == "NETWORK" }.items)
            .extracting<String> { it.question }.containsExactly(networkProblem.questionText)
        // 대표 개념이 두 카테고리 밖인 나머지 6개 카테고리는 문제를 받지 못해 빈 목록이다.
        assertThat(result.filter { it.category != "DATA_STRUCTURE" && it.category != "NETWORK" })
            .allSatisfy { group: CategoryHistoryResponse -> assertThat(group.items).isEmpty() }
    }

    @Test
    fun `세션에서 제출한 답을 문제 id 기준으로 복원한다`() {
        // Problem은 영속화 전이라 id가 기본값 0이므로, 제출 답 맵의 키도 0으로 맞춘다.
        val problem = problemOf("개념1", ProblemCategory.DATA_STRUCTURE)

        given(userRepository.existsById(USER_ID)).willReturn(true)
        given(learningHistory.findSolvedProblemIds(USER_ID)).willReturn(setOf(0L))
        given(problemRepository.findAllById(setOf(0L))).willReturn(listOf(problem))
        given(sessionRepository.findSolvedItemAnswers(USER_ID)).willReturn(listOf(solvedItemAnswer(0L, "정답1")))

        val result = service.findByCategory(USER_ID)

        val item = result.first { it.category == "DATA_STRUCTURE" }.items.single()
        assertThat(item.submittedAnswer).isEqualTo("정답1")
    }

    @Test
    fun `추가 학습에서만 푼 문제는 제출한 답이 없어 null이다`() {
        val problem = problemOf("개념1", ProblemCategory.DATA_STRUCTURE)

        given(userRepository.existsById(USER_ID)).willReturn(true)
        given(learningHistory.findSolvedProblemIds(USER_ID)).willReturn(setOf(0L))
        given(problemRepository.findAllById(setOf(0L))).willReturn(listOf(problem))
        // 세션 제출 이력이 비어 있다 — 추가 학습은 열린 항목이 승격되며 제출 답을 보존하지 않는다([ExtraStudyItem]).
        given(sessionRepository.findSolvedItemAnswers(USER_ID)).willReturn(emptyList())

        val result = service.findByCategory(USER_ID)

        val item = result.first { it.category == "DATA_STRUCTURE" }.items.single()
        assertThat(item.submittedAnswer).isNull()
    }

    private fun problemOf(conceptName: String, category: ProblemCategory): Problem =
        Problem(
            questionText = "$conceptName 질문",
            concepts = listOf(Concept(conceptName, category = category)),
            acceptableAnswers = setOf("정답"),
            representativeAnswer = "정답",
        )

    private fun solvedItemAnswer(problemId: Long, submittedAnswer: String?): SessionRepository.SolvedItemAnswer =
        object : SessionRepository.SolvedItemAnswer {
            override val problemId: Long = problemId
            override val submittedAnswer: String? = submittedAnswer
        }
}
