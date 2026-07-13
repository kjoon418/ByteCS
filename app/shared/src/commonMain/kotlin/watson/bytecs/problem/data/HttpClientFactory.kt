package watson.bytecs.problem.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 플랫폼별 HTTP 엔진과 API 베이스 URL을 공급한다.
 * 공통 설정(JSON 협상 등)은 [createProblemHttpClient]에 응집하고, 엔진만 expect/actual로 나눈다.
 */

/** Android=OkHttp, iOS=Darwin 엔진을 반환한다. */
expect fun defaultHttpClientEngine(): HttpClientEngine

/**
 * 플랫폼별 백엔드 베이스 URL.
 * Android 에뮬레이터는 호스트 루프백이 10.0.2.2, iOS 시뮬레이터는 호스트와 loopback을 공유한다.
 */
expect fun platformApiBaseUrl(): String

/** 공통 설정을 적용한 HttpClient. 알 수 없는 필드는 무시해 서버 확장에 견디게 한다. */
fun createProblemHttpClient(engine: HttpClientEngine = defaultHttpClientEngine()): HttpClient =
    HttpClient(engine) {
        // 비-2xx 응답을 예외로 올려, 저장소·뷰모델이 오류 상태를 명시적으로 다루게 한다.
        expectSuccess = true
        // 죽은 서버에서 로딩/전송 스피너가 엔진 기본값(수십 초)까지 멈추지 않도록 상한을 둔다.
        // 타임아웃 예외는 이미 친절한 Error/submitFailed 흐름으로 처리된다.
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            // ignoreUnknownKeys만으로 서버 확장에 견딘다. isLenient는 잘못된 페이로드를 숨길 수 있어 쓰지 않는다.
            json(Json { ignoreUnknownKeys = true })
        }
    }
