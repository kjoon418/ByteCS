package watson.bytecs.account.data

import kotlinx.serialization.Serializable
import watson.bytecs.account.Account
import watson.bytecs.account.AuthSession
import watson.bytecs.account.GuestSession
import watson.bytecs.account.PreferredDifficulty
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

/**
 * `GET /api/users/me`·`PATCH /api/users/me/settings` 응답. 게스트는 email이 null.
 * preferredDifficulty는 미설정 시 null(설정 화면이 '자동'으로 표시) — 모르는 값도 null로 접어 미설정과 동일 취급한다.
 */
@Serializable
internal data class UserResponseDto(
    val userId: Long,
    val role: String,
    val email: String? = null,
    val dailySessionSize: Int,
    val preferredDifficulty: String? = null,
) {
    fun toDomain(): Account = Account(
        userId = userId,
        role = Role.from(role),
        email = email,
        dailySessionSize = dailySessionSize,
        preferredDifficulty = preferredDifficulty?.let { PreferredDifficulty.from(it) },
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

/** `PATCH /api/users/me/settings` 요청 본문(세션 크기 변경 — 부분 갱신이라 이 필드만 보낸다). */
@Serializable
internal data class UpdateSettingsRequestDto(
    val dailySessionSize: Int,
)

/** `PATCH /api/users/me/settings` 요청 본문(선호 난이도 변경 — 부분 갱신이라 이 필드만 보낸다). */
@Serializable
internal data class UpdatePreferredDifficultyRequestDto(
    val preferredDifficulty: String,
)

/**
 * `PATCH /api/users/me/settings` 요청 본문(선호 난이도를 미설정(자동)으로 되돌리는 전용 액션 플래그).
 * `preferredDifficulty`와 동시 지정하면 서버가 400으로 거르므로 이 필드만 단독으로 보낸다.
 * ⭐️ 기본값 금지 — encodeDefaults=false라 기본값 필드는 직렬화에서 빠져 본문이 `{}`(무동작)가 된다.
 */
@Serializable
internal data class ResetPreferredDifficultyRequestDto(
    val resetPreferredDifficulty: Boolean,
)

/**
 * `PATCH /api/users/me/settings` 요청 본문(난이도 제안 거절 기록 — 부분 갱신이라 이 필드만 보낸다).
 * `true`만 의미가 있다(응답했음 = 다시 묻지 않음).
 * ⭐️ 기본값을 주면 안 된다 — 클라 Json은 encodeDefaults=false라 기본값 필드가 직렬화에서 빠져
 * 본문이 `{}`(무동작 PATCH)가 된다. 항상 명시적으로 넣어 필드가 반드시 인코딩되게 한다.
 */
@Serializable
internal data class DismissDifficultyPromptRequestDto(
    val difficultyPromptDone: Boolean,
)

/**
 * 비-2xx 응답 본문(서버 `common.error.ErrorResponse`와 대응). 400(INVALID_INPUT) 번역 시
 * 서버가 만든 상세 메시지를 그대로 안내하기 위해 파싱한다.
 */
@Serializable
internal data class ErrorResponseDto(
    val message: String,
    val errorCode: String,
)
