package watson.bytecs.account.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import watson.bytecs.problem.data.defaultHttpClientEngine

/**
 * 문제·계정 요청 모두가 공유하는 인증 HTTP 클라이언트.
 *
 * [tokenProvider]를 **전송 시점마다** 읽어 `Authorization: Bearer <token>` 헤더를 붙인다
 * (요청 파이프라인 훅이라 매 요청 평가되므로, 게스트→회원 토큰 교체가 클라이언트 재생성 없이 즉시 반영된다).
 * 토큰이 없으면 헤더를 아예 붙이지 않아, 인증 없는 경로(게스트 발급 등)와 기존 MockEngine 테스트가 그대로 동작한다.
 *
 * ⭐️ 방어적 스코프: [apiHost]가 주어지면 그 호스트로 가는 요청에만 토큰을 붙인다(다른 호스트로의 토큰 유출 차단).
 * null이면(테스트 등) 호스트를 가리지 않고 붙인다. 호스트는 요청 URL이 확정된 뒤(onRequest) 평가한다
 * — defaultRequest는 호출 URL이 적용되기 *전에* 실행돼 호스트를 알 수 없으므로 쓰지 않는다.
 *
 * 공통 설정([expectSuccess]·10초 타임아웃·ignoreUnknownKeys JSON 협상)은 문제 슬라이스의
 * `createProblemHttpClient`와 동일하게 유지한다.
 */
fun createAuthenticatedHttpClient(
    tokenProvider: () -> String?,
    apiHost: String? = null,
    engine: HttpClientEngine = defaultHttpClientEngine(),
): HttpClient {
    // 요청 URL이 확정된 시점에 최신 토큰을 읽어, (호스트가 맞으면) Authorization 헤더를 붙인다.
    val hostScopedAuth = createClientPlugin("HostScopedAuth") {
        onRequest { request, _ ->
            val token = tokenProvider()
            if (token != null && (apiHost == null || request.url.host == apiHost)) {
                request.headers.append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }

    return HttpClient(engine) {
        // 비-2xx 응답을 예외로 올려, 저장소·뷰모델이 오류 상태를 명시적으로 다루게 한다.
        expectSuccess = true
        // 죽은 서버에서 스피너가 엔진 기본값(수십 초)까지 멈추지 않도록 상한을 둔다.
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(hostScopedAuth)
    }
}
