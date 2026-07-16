package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 대표 정답 불변식 검증 — 화면의 대표 정답을 그대로 따라 입력하면 반드시 통과해야 하므로,
 * 정규화([AnswerText]) 기준으로 허용답 집합에 포함되어야 한다(오너 결정 2026-07-16).
 */
class ProblemRepresentativeAnswerTest {

    @Test
    fun `대표 정답이 정규화 기준으로 허용답에 없으면 생성이 거부된다`() {
        assertThatThrownBy {
            newProblem(
                acceptableAnswers = setOf("스택", "stack"),
                representativeAnswer = "큐",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("대표 정답은 정규화 기준으로 허용답 집합에 포함되어야 합니다.")
    }

    @Test
    fun `대표 정답이 비어 있으면 생성이 거부된다`() {
        assertThatThrownBy {
            newProblem(
                acceptableAnswers = setOf("스택"),
                representativeAnswer = "   ",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("대표 정답은 비어 있을 수 없습니다.")
    }

    @Test
    fun `대소문자만 다른 대표 정답도 정규화 뒤 허용답에 있으면 통과한다`() {
        // "TCP"는 소문자화 뒤 "tcp"로 허용답에 있으므로 병기 등재 없이도 불변식을 만족한다.
        assertThatCode {
            newProblem(
                acceptableAnswers = setOf("tcp", "티씨피"),
                representativeAnswer = "TCP",
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `병기 표기는 그 문자열 자체가 허용답에 등재돼 있어야 통과한다`() {
        // AnswerText는 구두점을 접지 않으므로 "스레드 (thread)"는 별도 등재가 필요하다.
        assertThatCode {
            newProblem(
                acceptableAnswers = setOf("스레드", "thread", "스레드 (thread)"),
                representativeAnswer = "스레드 (thread)",
            )
        }.doesNotThrowAnyException()
    }

    private fun newProblem(acceptableAnswers: Set<String>, representativeAnswer: String): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept("개념")),
            acceptableAnswers = acceptableAnswers,
            representativeAnswer = representativeAnswer,
            type = ProblemType.DEFINITION_RECALL,
            difficulty = Difficulty.EASY,
        )
}
