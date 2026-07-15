package watson.bytecs.scrap.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import watson.bytecs.problem.data.platformApiBaseUrl
import watson.bytecs.scrap.ScrapDetail
import watson.bytecs.scrap.ScrapListItem
import watson.bytecs.scrap.ScrapRepository

/**
 * 백엔드 REST API에 붙는 [ScrapRepository] 구현.
 *  - `GET    {baseUrl}/api/scraps`                      내 스크랩 목록
 *  - `GET    {baseUrl}/api/scraps/{problemId}`          읽기 전용 재열람
 *  - `POST   {baseUrl}/api/problems/{problemId}/scraps` 스크랩 추가(멱등)
 *  - `DELETE {baseUrl}/api/problems/{problemId}/scraps` 스크랩 해제
 *
 * 인증 헤더는 [client]가 요청마다 붙이므로 여기서 토큰을 다루지 않는다. 비-2xx는 예외로 올라온다.
 */
class KtorScrapRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : ScrapRepository {

    override suspend fun list(): List<ScrapListItem> {
        val dtos: List<ScrapListItemDto> = client.get("$baseUrl/api/scraps").body()
        return dtos.map { it.toDomain() }
    }

    override suspend fun get(problemId: Long): ScrapDetail {
        val dto: ScrapDetailDto = client.get("$baseUrl/api/scraps/$problemId").body()
        return dto.toDomain()
    }

    override suspend fun add(problemId: Long) {
        client.post("$baseUrl/api/problems/$problemId/scraps")
    }

    override suspend fun remove(problemId: Long) {
        client.delete("$baseUrl/api/problems/$problemId/scraps")
    }

    // close()는 재정의하지 않는다(기본 no-op). [client] 수명은 소유자(App)가 단독으로 닫는다.
}
