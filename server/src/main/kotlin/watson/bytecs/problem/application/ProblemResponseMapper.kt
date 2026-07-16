package watson.bytecs.problem.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.application.dto.AttemptResponse
import watson.bytecs.problem.application.dto.NextProblemResponse
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem

/**
 * 도메인 엔티티를 응답 DTO로 변환한다.
 * 정답이 아닐 때 개념·해설·심화 정보가 새어 나가지 않도록 노출 규칙을 이 한곳에 응집한다.
 */
@Component
class ProblemResponseMapper {

    fun toNextProblemResponse(problem: Problem): NextProblemResponse =
        NextProblemResponse(
            id = problem.id,
            question = problem.questionText,
            difficulty = problem.difficulty?.name,
            codeSnippet = problem.codeSnippet,
        )

    fun toAttemptResponse(problem: Problem, judgement: Judgement): AttemptResponse {
        val answerRevealed = judgement == Judgement.CORRECT

        return AttemptResponse(
            result = judgement.name,
            concepts = if (answerRevealed) problem.conceptNames() else null,
            explanation = if (answerRevealed) problem.explanation else null,
            enrichment = if (answerRevealed) problem.enrichment else null,
            representativeAnswer = if (answerRevealed) problem.representativeAnswer else null,
        )
    }
}
