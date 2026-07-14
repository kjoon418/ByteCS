package watson.bytecs.account.presentation.request

import jakarta.validation.constraints.NotBlank

/**
 * 회원 가입 요청. 필수 값 누락만 여기서 검증하고, 형식·길이 규칙은 도메인 VO(Email·RawPassword)가 강제한다.
 */
data class RegisterRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String?,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String?,
)
