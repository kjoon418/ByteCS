package watson.bytecs.problem.application.dto

/**
 * 답 제출 결과 응답.
 * 개념·심화 정보는 정답(CORRECT)일 때만 채워지고, 불일치·근접일 때는 무낙인·비노출 원칙에 따라 null이다.
 */
data class AttemptResponse(
    val result: String,
    val concept: String?,
    val explanation: String?,
)
