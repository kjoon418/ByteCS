package watson.bytecs.problem.presentation.request

import jakarta.validation.constraints.NotBlank

/**
 * 답 제출 요청. API 계약상 필수 값인 answer의 누락 여부만 검증한다.
 */
data class AttemptRequest(
    @field:NotBlank(message = "답변은 필수입니다.")
    val answer: String?,
)
