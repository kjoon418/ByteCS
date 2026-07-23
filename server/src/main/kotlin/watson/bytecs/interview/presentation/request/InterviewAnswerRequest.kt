package watson.bytecs.interview.presentation.request

import jakarta.validation.constraints.NotBlank

/** 면접 세션 답 제출 요청. 자기 말로 쓴 자유 설명이라 정규화하지 않고 그대로 채점기에 전달한다. */
data class InterviewAnswerRequest(
    @field:NotBlank(message = "설명은 필수입니다.")
    val explanation: String?,
)
