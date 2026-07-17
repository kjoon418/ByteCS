package watson.bytecs.extrastudy.application.dto

import watson.bytecs.problem.application.dto.EnrichmentResponse

/**
 * 추가 학습 답 제출 결과 응답. 세션 attempt 응답에서 세션 전용 필드(status·solvedCount·totalCount·position·streak·currentProblem)만 뺀 모양이다.
 *  - result: 판정(CORRECT/NEAR_MISS/MISMATCH). 비정답도 200으로 응답한다(무낙인).
 *  - concepts·explanation·enrichment·representativeAnswer: 정답(CORRECT)일 때만 채워진다(concepts는 태깅 순). 비정답에서는 no-leak으로 null.
 *    enrichment(심화 정보)는 정답이어도 문제에 없으면 null(graceful).
 *  - misconceptionHint: 비정답이고 제출이 예상 오답 집합과 정규화 후 일치할 때만 채워진다(그 외 null). 실려도 오답으로 확정되지 않고 정답을 노출하지 않으며, 이때 result는 MISMATCH로 확정된다(근접보다 우선).
 *  - 정답이어도 다음 문제를 싣지 않는다 — 클라가 GET /current를 다시 불러 다음(또는 소진)을 받는다(단일 항목 모델).
 */
data class ExtraStudyAttemptResponse(
    val result: String,
    val concepts: List<String>?,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String?,
    val misconceptionHint: String?,
)
