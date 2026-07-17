package watson.bytecs.categoryhistory.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import watson.bytecs.categoryhistory.CategoryHistoryGroup
import watson.bytecs.categoryhistory.CategoryHistoryRepository
import watson.bytecs.problem.data.platformApiBaseUrl

/**
 * 백엔드 REST API에 붙는 [CategoryHistoryRepository] 구현.
 *  - `GET {baseUrl}/api/learning-history/categories` 카테고리별 학습 이력(1차, 읽기 전용)
 *
 * 인증 헤더는 [client]가 요청마다 붙이므로 여기서 토큰을 다루지 않는다. 비-2xx는 예외로 올라온다.
 */
class KtorCategoryHistoryRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : CategoryHistoryRepository {

    override suspend fun getByCategory(): List<CategoryHistoryGroup> {
        val dtos: List<CategoryHistoryGroupDto> = client.get("$baseUrl/api/learning-history/categories").body()
        return dtos.map { it.toDomain() }
    }
}
