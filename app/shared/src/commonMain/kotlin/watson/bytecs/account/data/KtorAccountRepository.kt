package watson.bytecs.account.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import watson.bytecs.account.Account
import watson.bytecs.account.AccountRepository
import watson.bytecs.account.AuthSession
import watson.bytecs.account.EmailAlreadyInUseException
import watson.bytecs.account.GuestSession
import watson.bytecs.account.InvalidCredentialsException
import watson.bytecs.problem.data.platformApiBaseUrl

/**
 * 백엔드 계정 REST API에 붙는 [AccountRepository] 구현. 판정·상태 전이는 전적으로 서버에 위임한다.
 *
 * 인증 헤더는 [client]가 요청마다 붙이므로(→ [createAuthenticatedHttpClient]) 여기서 토큰을 다루지 않는다.
 * 특정 실패(가입 이메일 중복 409, 로그인 401)는 사용자 언어로 안내하기 위해 도메인 예외로 번역하고,
 * 그 밖의 비-2xx·네트워크 오류는 시스템 오류로 그대로 올린다(뷰모델이 "학습 기록은 안전해요"로 처리).
 */
class KtorAccountRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : AccountRepository {

    override suspend fun issueGuest(): GuestSession {
        val dto: GuestResponseDto = client.post("$baseUrl/api/guests").body()
        return dto.toDomain()
    }

    override suspend fun register(email: String, password: String): AuthSession {
        val dto: TokenResponseDto = translating {
            client.post("$baseUrl/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequestDto(email = email, password = password))
            }.body()
        }
        return dto.toDomain()
    }

    override suspend fun login(email: String, password: String): AuthSession {
        val dto: TokenResponseDto = translating {
            client.post("$baseUrl/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequestDto(email = email, password = password))
            }.body()
        }
        return dto.toDomain()
    }

    override suspend fun getMe(): Account {
        val dto: UserResponseDto = client.get("$baseUrl/api/users/me").body()
        return dto.toDomain()
    }

    override suspend fun updateSettings(dailySessionSize: Int): Account {
        val dto: UserResponseDto = client.patch("$baseUrl/api/users/me/settings") {
            contentType(ContentType.Application.Json)
            setBody(UpdateSettingsRequestDto(dailySessionSize))
        }.body()
        return dto.toDomain()
    }

    override suspend fun deleteMe() {
        client.delete("$baseUrl/api/users/me")
    }

    /**
     * 가입·로그인 응답의 비-2xx를 사용자 언어 도메인 예외로 번역한다.
     *  - 409 → [EmailAlreadyInUseException](가입 이메일 중복).
     *  - 401 → [InvalidCredentialsException](로그인 실패, 원인 비구분).
     * 그 밖의 상태는 시스템 오류로 그대로 전파한다.
     */
    private inline fun <T> translating(block: () -> T): T =
        try {
            block()
        } catch (error: ResponseException) {
            when (error.response.status) {
                HttpStatusCode.Conflict -> throw EmailAlreadyInUseException()
                HttpStatusCode.Unauthorized -> throw InvalidCredentialsException()
                else -> throw error
            }
        }
}
