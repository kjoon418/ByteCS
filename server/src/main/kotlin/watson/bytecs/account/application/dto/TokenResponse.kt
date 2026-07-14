package watson.bytecs.account.application.dto

/**
 * 가입·로그인 성공 응답. 이후 요청에 사용할 인증 토큰만 전달한다.
 */
data class TokenResponse(
    val token: String,
)
