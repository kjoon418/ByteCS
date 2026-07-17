package watson.bytecs.problem.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.ZoneId
import java.util.Optional
import kotlin.random.Random

/**
 * 서비스 슬라이스 테스트.
 * Repository·협력자는 Mockito로 대체하고, 순수 무상태 협력자인 [ProblemResponseMapper]는 실제 객체를 써
 * 응답 매핑 결과(무낙인·비노출)까지 함께 검증한다.
 * 추가 연습의 다음 문제 선정은 시드 고정 [Random]으로 결정적으로 검증한다.
 */
class ProblemServiceTest {

    private val problemRepository = mock(ProblemRepository::class.java)
    private val sessionRepository = mock(SessionRepository::class.java)
    private val reviewService = mock(ReviewService::class.java)

    private companion object {
        const val USER_ID = 7L
        const val PROBLEM_ID = 42L
        const val CONCEPT_NAME = "해시 충돌"
        const val EXPLANATION = "체이닝, 개방 주소법 등으로 해소한다."
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val TODAY = java.time.LocalDate.of(2026, 7, 14)
    }

    private val fixedClock: Clock = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST)

    /** 시드를 고정해 선정 결과를 결정적으로 만든 서비스. */
    private fun service(random: Random = Random(1L)): ProblemService =
        ProblemService(problemRepository, sessionRepository, reviewService, ProblemResponseMapper(), fixedClock, random)

    @Nested
    inner class 다음_문제를_조회한다 {

        @Test
        fun 아직_풀지_않은_문제_중에서_고르고_이미_푼_문제는_제외한다() {
            // all=1,2,3 중 1·2는 이미 풀었으므로 안 푼 3만 후보다.
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(listOf(1L, 2L, 3L))
            given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(1L, 2L))
            given(problemRepository.findById(3L)).willReturn(Optional.of(defaultProblem()))

            val response = service().findNextProblem(USER_ID)

            assertThat(response.question).isEqualTo("질문")
            verify(problemRepository).findById(3L)
            verify(problemRepository, never()).findById(1L)
            verify(problemRepository, never()).findById(2L)
        }

        @Test
        fun 안_푼_문제가_없으면_도래한_복습_문제를_고른다() {
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(listOf(1L, 2L, 3L))
            given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(1L, 2L, 3L))
            given(sessionRepository.findAssignedProblemIds(USER_ID)).willReturn(listOf(1L, 2L, 3L))
            given(reviewService.selectDueReviewProblemIds(USER_ID, TODAY, setOf(1L, 2L, 3L), setOf(1L, 2L, 3L)))
                .willReturn(listOf(2L))
            given(problemRepository.findById(2L)).willReturn(Optional.of(defaultProblem()))

            val response = service().findNextProblem(USER_ID)

            assertThat(response.question).isEqualTo("질문")
            verify(problemRepository).findById(2L)
        }

        @Test
        fun 안_푼_문제도_도래한_복습도_없으면_전체에서_폴백한다() {
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(listOf(5L))
            given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(listOf(5L))
            given(sessionRepository.findAssignedProblemIds(USER_ID)).willReturn(listOf(5L))
            given(reviewService.selectDueReviewProblemIds(USER_ID, TODAY, setOf(5L), setOf(5L)))
                .willReturn(emptyList())
            given(problemRepository.findById(5L)).willReturn(Optional.of(defaultProblem()))

            val response = service().findNextProblem(USER_ID)

            assertThat(response.question).isEqualTo("질문")
            verify(problemRepository).findById(5L)
        }

        @Test
        fun 토큰이_없으면_개인화_없이_전체에서_무작위로_폴백한다() {
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(listOf(9L))
            given(problemRepository.findById(9L)).willReturn(Optional.of(defaultProblem()))

            val response = service().findNextProblem(null)

            assertThat(response.question).isEqualTo("질문")
            verify(problemRepository).findById(9L)
            // 무토큰이면 사용자별 학습 상태를 조회하지 않는다.
            verify(sessionRepository, never()).findSolvedProblemIds(anyLong())
        }

        @Test
        fun 등록된_문제가_없으면_예외를_던진다() {
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(emptyList())

            assertThatThrownBy { service().findNextProblem(USER_ID) }
                .isInstanceOf(ProblemNotFoundException::class.java)
        }

        @Test
        fun 반복_호출하면_같은_문제만_반복하지_않는다() {
            // 안 푼 후보가 여럿이면 호출마다 무작위로 흩어져, 항상 같은 문제만 반환되지 않는다(QA #7).
            given(problemRepository.findAllIdsOrderByIdAsc()).willReturn(listOf(1L, 2L, 3L, 4L, 5L))
            given(sessionRepository.findSolvedProblemIds(USER_ID)).willReturn(emptyList())
            given(problemRepository.findById(anyLong())).willReturn(Optional.of(defaultProblem()))

            val service = service(Random(1L))
            repeat(30) { service.findNextProblem(USER_ID) }

            val captor = ArgumentCaptor.forClass(Long::class.java)
            verify(problemRepository, org.mockito.Mockito.atLeast(30)).findById(captor.capture())
            assertThat(captor.allValues.toSet().size).isGreaterThan(1)
        }
    }

    @Nested
    inner class 답을_제출한다 {

        @Test
        fun 정답이면_CORRECT와_함께_개념과_해설을_공개한다() {
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            val response = service().submitAnswer(PROBLEM_ID, AnswerText("collision"))

            assertThat(response.result).isEqualTo("CORRECT")
            assertThat(response.concepts).containsExactly(CONCEPT_NAME)
            assertThat(response.explanation).isEqualTo(EXPLANATION)
            assertThat(response.representativeAnswer).isEqualTo("해시 충돌")
        }

        @Test
        fun 오답이면_MISMATCH이고_개념과_해설을_노출하지_않는다() {
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            val response = service().submitAnswer(PROBLEM_ID, AnswerText("자바"))

            assertThat(response.result).isEqualTo("MISMATCH")
            assertThat(response.concepts).isNull()
            assertThat(response.explanation).isNull()
            assertThat(response.representativeAnswer).isNull()
        }

        @Test
        fun 오탈자_수준의_답이면_NEAR_MISS이고_개념과_해설을_노출하지_않는다() {
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            val response = service().submitAnswer(PROBLEM_ID, AnswerText("collsion"))

            assertThat(response.result).isEqualTo("NEAR_MISS")
            assertThat(response.concepts).isNull()
            assertThat(response.explanation).isNull()
            assertThat(response.representativeAnswer).isNull()
        }

        @Test
        fun 존재하지_않는_문제면_예외를_던진다() {
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.empty())

            assertThatThrownBy { service().submitAnswer(PROBLEM_ID, AnswerText("collision")) }
                .isInstanceOf(ProblemNotFoundException::class.java)
        }
    }

    private fun defaultProblem(): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept(CONCEPT_NAME)),
            acceptableAnswers = setOf("해시 충돌", "충돌", "collision"),
            representativeAnswer = "해시 충돌",
            // 개념 이름을 묻는 문제라 근접 판정 대상이다. (유형이 없으면 근접이 꺼져 NEAR_MISS 자체가 나오지 않는다)
            type = ProblemType.DEFINITION_RECALL,
            difficulty = Difficulty.MEDIUM,
            explanation = EXPLANATION,
        )
}
