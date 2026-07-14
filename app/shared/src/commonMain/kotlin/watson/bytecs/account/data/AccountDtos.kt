package watson.bytecs.account.data

import kotlinx.serialization.Serializable
import watson.bytecs.account.Account
import watson.bytecs.account.AuthSession
import watson.bytecs.account.GuestSession
import watson.bytecs.account.Role

/**
 * 백엔드 계정 API 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리한다.
 */

/** `POST /api/guests` 응답(201). */
@Serializable
internal data class GuestResponseDto(
    val token: String,
    val userId: Long,
    val role: String,
) {
    fun toDomain(): GuestSession = GuestSession(token = token, userId = userId, role = Role.from(role))
}

/** `POST /api/auth/register`·`/login` 응답(200). 서버는 토큰만 돌려준다. */
@Serializable
internal data class TokenResponseDto(
    val token: String,
) {
    fun toDomain(): AuthSession = AuthSession(token = token)
}

/** `GET /api/users/me`·`PATCH /api/users/me/settings` 응답. 게스트는 email이 null. */
@Serializable
internal data class UserResponseDto(
    val userId: Long,
    val role: String,
    val email: String? = null,
    val dailySessionSize: Int,
) {
    fun toDomain(): Account = Account(
        userId = userId,
        role = Role.from(role),
        email = email,
        dailySessionSize = dailySessionSize,
    )
}

/** `POST /api/auth/register` 요청 본문. */
@Serializable
internal data class RegisterRequestDto(
    val email: String,
    val password: String,
)

/** `POST /api/auth/login` 요청 본문. */
@Serializable
internal data class LoginRequestDto(
    val email: String,
    val password: String,
)

/** `PATCH /api/users/me/settings` 요청 본문. */
@Serializable
internal data class UpdateSettingsRequestDto(
    val dailySessionSize: Int,
)
