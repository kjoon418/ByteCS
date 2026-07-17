package watson.bytecs.study.application

import org.springframework.stereotype.Component
import watson.bytecs.problem.application.dto.EnrichmentResponse
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.study.application.dto.CategoryHistoryItemResponse
import watson.bytecs.study.application.dto.CategoryHistoryResponse

/**
 * 대표 분류로 묶인 '푼 문제'를 카테고리별 학습 이력 응답 DTO로 변환한다.
 * 이미 정답으로 통과한 문제만 다루므로, 지난 문제 다시 보기·스크랩 상세와 같은 이유로 개념·모범답안·해설을 공개한다.
 */
@Component
class CategoryHistoryResponseMapper {

    /** 한 카테고리의 그룹 응답을 만든다. [problems]가 비어 있으면 items도 빈 목록('준비 중'). */
    fun toGroupResponse(
        category: ProblemCategory,
        problems: List<Problem>,
        submittedAnswersByProblemId: Map<Long, String?>,
    ): CategoryHistoryResponse =
        CategoryHistoryResponse(
            category = category.name,
            items = problems
                .sortedBy { it.id }
                .map { toItemResponse(it, submittedAnswersByProblemId[it.id]) },
        )

    private fun toItemResponse(problem: Problem, submittedAnswer: String?): CategoryHistoryItemResponse =
        CategoryHistoryItemResponse(
            problemId = problem.id,
            question = problem.questionText,
            codeSnippet = problem.codeSnippet,
            difficulty = problem.difficulty?.name,
            submittedAnswer = submittedAnswer,
            // 이 목록은 '정답으로 통과한' 문제만 담으므로 판정은 항상 CORRECT다(PastItemResponse와 같은 규약).
            result = Judgement.CORRECT.name,
            concepts = problem.conceptNames(),
            explanation = problem.explanation,
            enrichment = problem.enrichment?.let(EnrichmentResponse::from),
            representativeAnswer = problem.representativeAnswer,
        )
}
