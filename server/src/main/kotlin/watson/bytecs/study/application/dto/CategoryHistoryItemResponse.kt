package watson.bytecs.study.application.dto

import watson.bytecs.problem.application.dto.EnrichmentResponse

/**
 * 카테고리별 학습 이력의 한 항목(읽기 전용). 세션·추가 학습을 통틀어 '정답으로 통과한' 문제만 실리므로,
 * result(판정)는 세션의 PastItemResponse·스크랩 상세와 같은 이유로 항상 CORRECT다.
 * submittedAnswer(내가 쓴 답)는 세션 출처만 보존된다 — 추가 학습은 열린 항목이 승격되며 제출 답을 남기지 않으므로([ExtraStudyItem]),
 * 추가 학습에서만 푼 문제는 이 필드가 null이다(graceful — 정답 여부·모범답안 등 나머지 정보는 그대로 제공).
 */
data class CategoryHistoryItemResponse(
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val difficulty: String?,
    val submittedAnswer: String?,
    val result: String,
    val concepts: List<String>,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String,
)
