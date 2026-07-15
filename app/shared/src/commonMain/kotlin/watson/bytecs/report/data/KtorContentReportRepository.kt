package watson.bytecs.report.data

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import watson.bytecs.problem.data.platformApiBaseUrl
import watson.bytecs.report.ContentReportRepository

/**
 * 백엔드 REST API에 붙는 [ContentReportRepository] 구현.
 *  - `POST {baseUrl}/api/problems/{problemId}/reports`  본문 `{ "message": ... }` → 201
 *
 * 인증 헤더는 [client]가 요청마다 붙이므로(→ createAuthenticatedHttpClient) 여기서 토큰을 다루지 않는다.
 * 비-2xx는 expectSuccess=true로 예외가 되어 뷰모델이 전송 실패로 친절히 처리한다.
 */
class KtorContentReportRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : ContentReportRepository {

    override suspend fun report(problemId: Long, message: String) {
        client.post("$baseUrl/api/problems/$problemId/reports") {
            contentType(ContentType.Application.Json)
            setBody(ContentReportRequestDto(message))
        }
    }

    // close()는 재정의하지 않는다(기본 no-op). [client] 수명은 소유자(App)가 단독으로 닫는다.
}
