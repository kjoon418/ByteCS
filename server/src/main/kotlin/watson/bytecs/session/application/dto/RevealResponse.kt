package watson.bytecs.session.application.dto

/**
 * 정답 공개(안전판) 응답. 공개 후에도 직접 정답을 입력해야 다음으로 넘어간다.
 * representativeAnswer는 화면 표시용 대표 정답(그대로 따라 입력하면 통과 — 불변식 보장), concepts·explanation은 학습 맥락이다(concepts는 태깅 순).
 * enrichment(심화 정보·'더 알아보기')는 정답 공개로 학습한 뒤에도 노출된다(명세 174행) — 문제에 없으면 null(graceful).
 */
data class RevealResponse(
    val concepts: List<String>,
    val explanation: String?,
    val enrichment: String?,
    val representativeAnswer: String,
)
