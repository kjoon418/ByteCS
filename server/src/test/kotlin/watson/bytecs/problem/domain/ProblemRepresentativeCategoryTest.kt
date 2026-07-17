package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 문제 대표 분류 도출([Problem.representativeCategory]) 검증 — 명세 §7 '대표 분류 원칙'(결정 2026-07-17).
 * 대표 개념(첫 번째 개념)의 카테고리를 그 문제의 대표 분류로 결정적으로 도출한다.
 */
class ProblemRepresentativeCategoryTest {

    @Test
    fun `대표 분류는 첫 번째 개념의 카테고리로 결정적으로 도출된다`() {
        val representative = Concept("스택", category = ProblemCategory.DATA_STRUCTURE)
        val other = Concept("힙 정렬", category = ProblemCategory.ALGORITHM)
        val problem = newProblem(concepts = listOf(representative, other))

        // 여러 개념·카테고리에 걸쳐도 대표 분류는 첫 번째 개념의 것 하나뿐이다.
        assertThat(problem.representativeCategory()).isEqualTo(ProblemCategory.DATA_STRUCTURE)
    }

    @Test
    fun `같은 입력이면 대표 분류 도출 결과는 항상 같다`() {
        val concepts = listOf(
            Concept("TCP 3-way handshake", category = ProblemCategory.NETWORK),
            Concept("정규화 제3정규형", category = ProblemCategory.DATABASE),
        )
        val problem = newProblem(concepts = concepts)

        val results = (1..5).map { problem.representativeCategory() }

        assertThat(results).containsOnly(ProblemCategory.NETWORK)
    }

    @Test
    fun `대표 개념이 미분류면 대표 분류도 null이다`() {
        val unclassified = Concept("아직 분류 안 된 개념")
        val problem = newProblem(concepts = listOf(unclassified, Concept("스택", category = ProblemCategory.DATA_STRUCTURE)))

        assertThat(problem.representativeCategory()).isNull()
    }

    @Test
    fun `개념이 하나뿐이어도 그 개념의 카테고리가 대표 분류다`() {
        val problem = newProblem(concepts = listOf(Concept("보안 취약점", category = ProblemCategory.SECURITY)))

        assertThat(problem.representativeCategory()).isEqualTo(ProblemCategory.SECURITY)
    }

    @Test
    fun `개념이 없는 문제는 애초에 생성될 수 없어 대표 분류 도출 대상이 아니다`() {
        // Problem의 기존 불변식(concepts.isNotEmpty())이 이미 이 경계를 막는다 —
        // representativeCategory()의 concepts.first()가 빈 리스트를 만날 일이 없다는 근거.
        assertThatThrownBy {
            newProblem(concepts = emptyList())
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("문제는 하나 이상의 개념에 연결되어야 합니다.")
    }

    private fun newProblem(concepts: List<Concept>): Problem =
        Problem(
            questionText = "질문",
            concepts = concepts,
            acceptableAnswers = setOf("정답"),
            representativeAnswer = "정답",
            type = ProblemType.DEFINITION_RECALL,
            difficulty = Difficulty.EASY,
        )
}
