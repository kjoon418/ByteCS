package watson.bytecs.problem.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import watson.bytecs.problem.AttemptResult
import watson.bytecs.problem.ProblemRepository
import watson.bytecs.problem.ProblemView

/**
 * 백엔드 REST API에 붙는 [ProblemRepository] 구현.
 *  - `GET  {baseUrl}/api/problems/next`
 *  - `POST {baseUrl}/api/problems/{id}/attempts`  본문 `{ "answer": ... }`
 *
 * 판정은 전적으로 서버에 위임한다(클라이언트는 판정하지 않는다 — 결정성·단일 출처).
 */
class KtorProblemRepository(
    private val client: HttpClient,
    private val baseUrl: String = platformApiBaseUrl(),
) : ProblemRepository {

    override suspend fun getNext(): ProblemView {
        val dto: NextProblemDto = client.get("$baseUrl/api/problems/next").body()
        return dto.toDomain()
    }

    override suspend fun submitAttempt(problemId: Long, answer: String): AttemptResult {
        val dto: AttemptResponseDto = client.post("$baseUrl/api/problems/$problemId/attempts") {
            contentType(ContentType.Application.Json)
            setBody(AttemptRequestDto(answer))
        }.body()
        return dto.toDomain()
    }

    /** 소유한 HttpClient를 닫는다. 앱 컴포지션 이탈 시 [watson.bytecs.App]에서 호출한다. */
    override fun close() {
        client.close()
    }
}
