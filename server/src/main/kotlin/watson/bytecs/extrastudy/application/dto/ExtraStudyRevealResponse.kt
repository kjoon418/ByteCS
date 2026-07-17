package watson.bytecs.extrastudy.application.dto

import watson.bytecs.problem.application.dto.EnrichmentResponse

/**
 * 추가 학습 정답 공개(안전판) 응답. 세션 RevealResponse와 동형이다.
 * representativeAnswer는 화면 표시용 대표 정답(그대로 따라 입력하면 통과), concepts·explanation은 학습 맥락(concepts는 태깅 순),
 * enrichment(심화 정보)는 정답 공개로 학습한 뒤에도 노출된다 — 문제에 없으면 null(graceful).
 * 공개해도 진행은 정답 제출로만 이뤄진다(서버가 자연히 강제).
 * category는 대표 분류(명세 §7). 미분류면 null.
 */
data class ExtraStudyRevealResponse(
    val concepts: List<String>,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String,
    val category: String?,
)
