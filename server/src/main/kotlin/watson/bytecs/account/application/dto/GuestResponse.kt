package watson.bytecs.account.application.dto

/**
 * 게스트 발급 응답. 발급한 토큰과 함께 식별자·역할을 실어, 클라이언트가 즉시 인증 상태를 구성하게 한다.
 */
data class GuestResponse(
    val token: String,
    val userId: Long,
    val role: String,
)
