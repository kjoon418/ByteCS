package watson.bytecs.problem.application

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ProblemRepository
import java.util.Optional

/**
 * 서비스 슬라이스 테스트.
 * Repository는 Mockito로 대체하고, 순수 무상태 협력자인 [ProblemResponseMapper]는 실제 객체를 써
 * 응답 매핑 결과(무낙인·비노출)까지 함께 검증한다.
 */
class ProblemServiceTest {

    private val problemRepository = mock(ProblemRepository::class.java)
    private val problemService = ProblemService(problemRepository, ProblemResponseMapper())

    private companion object {
        const val PROBLEM_ID = 42L
        const val CONCEPT_NAME = "해시 충돌"
        const val EXPLANATION = "체이닝, 개방 주소법 등으로 해소한다."
    }

    @Nested
    inner class 다음_문제를_조회한다 {

        @Test
        fun 등록된_문제가_있으면_문제_정보를_반환한다() {
            // given
            given(problemRepository.findFirstByOrderByIdAsc()).willReturn(defaultProblem())

            // when
            val response = problemService.findNextProblem()

            // then
            assertThat(response.question).isEqualTo("질문")
            assertThat(response.difficulty).isEqualTo(Difficulty.MEDIUM.name)
        }

        @Test
        fun 등록된_문제가_없으면_예외를_던진다() {
            // given
            given(problemRepository.findFirstByOrderByIdAsc()).willReturn(null)

            // when and then
            assertThatThrownBy { problemService.findNextProblem() }
                .isInstanceOf(ProblemNotFoundException::class.java)
        }
    }

    @Nested
    inner class 답을_제출한다 {

        @Test
        fun 정답이면_CORRECT와_함께_개념과_해설을_공개한다() {
            // given
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            // when
            val response = problemService.submitAnswer(PROBLEM_ID, AnswerText("collision"))

            // then
            assertThat(response.result).isEqualTo("CORRECT")
            assertThat(response.concepts).containsExactly(CONCEPT_NAME)
            assertThat(response.explanation).isEqualTo(EXPLANATION)
        }

        @Test
        fun 오답이면_MISMATCH이고_개념과_해설을_노출하지_않는다() {
            // given
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            // when
            val response = problemService.submitAnswer(PROBLEM_ID, AnswerText("자바"))

            // then
            assertThat(response.result).isEqualTo("MISMATCH")
            assertThat(response.concepts).isNull()
            assertThat(response.explanation).isNull()
        }

        @Test
        fun 오탈자_수준의_답이면_NEAR_MISS이고_개념과_해설을_노출하지_않는다() {
            // given
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.of(defaultProblem()))

            // when
            val response = problemService.submitAnswer(PROBLEM_ID, AnswerText("collsion"))

            // then
            assertThat(response.result).isEqualTo("NEAR_MISS")
            assertThat(response.concepts).isNull()
            assertThat(response.explanation).isNull()
        }

        @Test
        fun 존재하지_않는_문제면_예외를_던진다() {
            // given
            given(problemRepository.findById(PROBLEM_ID)).willReturn(Optional.empty())

            // when and then
            assertThatThrownBy { problemService.submitAnswer(PROBLEM_ID, AnswerText("collision")) }
                .isInstanceOf(ProblemNotFoundException::class.java)
        }
    }

    private fun defaultProblem(): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept(CONCEPT_NAME)),
            acceptableAnswers = setOf("해시 충돌", "충돌", "collision"),
            // 개념 이름을 묻는 문제라 근접 판정 대상이다. (유형이 없으면 근접이 꺼져 NEAR_MISS 자체가 나오지 않는다)
            type = ProblemType.DEFINITION_RECALL,
            difficulty = Difficulty.MEDIUM,
            explanation = EXPLANATION,
        )
}
