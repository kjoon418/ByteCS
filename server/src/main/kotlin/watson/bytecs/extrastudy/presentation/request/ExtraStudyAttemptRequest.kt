package watson.bytecs.extrastudy.presentation.request

import jakarta.validation.constraints.NotBlank

/**
 * 추가 학습 답 제출 요청. API 계약상 필수 값인 answer의 누락 여부만 검증한다(정규화는 도메인 VO가 맡는다).
 */
data class ExtraStudyAttemptRequest(
    @field:NotBlank(message = "답변은 필수입니다.")
    val answer: String?,
)
