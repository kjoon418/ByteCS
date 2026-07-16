package watson.bytecs.problem.application.dto

/**
 * 답 제출 결과 응답.
 * 개념·해설은 정답(CORRECT)일 때만 채워지고, 불일치·근접일 때는 무낙인·비노출 원칙에 따라 null이다(concepts는 태깅 순).
 * representativeAnswer(화면 표시용 대표 정답)도 정답일 때만 실린다. enrichment(심화 정보·'더 알아보기')도 같은 규칙 — 정답일 때만, 문제에 없으면 정답이어도 null.
 */
data class AttemptResponse(
    val result: String,
    val concepts: List<String>?,
    val explanation: String?,
    val enrichment: EnrichmentResponse?,
    val representativeAnswer: String?,
)
