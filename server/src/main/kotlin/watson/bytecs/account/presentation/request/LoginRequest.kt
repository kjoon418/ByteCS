package watson.bytecs.account.presentation.request

import jakarta.validation.constraints.NotBlank

/**
 * 로그인 요청. 필수 값 누락만 검증한다.
 */
data class LoginRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String?,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String?,
)
