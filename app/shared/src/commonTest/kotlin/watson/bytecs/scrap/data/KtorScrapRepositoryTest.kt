package watson.bytecs.scrap.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import watson.bytecs.problem.data.createProblemHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KtorScrapRepository가 스크랩 목록·재열람·추가·해제를 계약대로 요청하고 매핑하는지
 * MockEngine으로 결정적으로 검증한다.
 */
class KtorScrapRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun list_hitsScrapsEndpoint_andMapsItems() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/scraps", request.url.toString())
            respond(
                content = """[{"problemId":7,"question":"해시 충돌이란?","scrappedAt":"2026-07-15"}]""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorScrapRepository(createProblemHttpClient(engine), baseUrl)

        val items = repository.list()

        assertEquals(1, items.size)
        assertEquals(7L, items[0].problemId)
        assertEquals("해시 충돌이란?", items[0].question)
        assertEquals("2026-07-15", items[0].scrappedAt)
    }

    @Test
    fun get_hitsScrapDetailEndpoint_andMapsModelAnswer() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/scraps/7", request.url.toString())
            respond(
                content = """
                    {"problemId":7,"question":"해시 충돌이란?","codeSnippet":null,
                     "concepts":["해시 충돌"],"explanation":"서로 다른 키가 같은 버킷으로 간다.",
                     "acceptableAnswers":["충돌","해시 충돌","collision"],
                     "enrichment":"해시 충돌은 생일 문제와 연결돼요."}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorScrapRepository(createProblemHttpClient(engine), baseUrl)

        val detail = repository.get(problemId = 7L)

        assertEquals(7L, detail.problemId)
        assertEquals(listOf("해시 충돌"), detail.concepts)
        assertEquals(listOf("충돌", "해시 충돌", "collision"), detail.acceptableAnswers)
        // 재열람도 정답 접근 허용 맥락이라 '더 알아보기'가 포함된다.
        assertEquals("해시 충돌은 생일 문제와 연결돼요.", detail.enrichment)
    }

    @Test
    fun add_postsToScrapsEndpoint() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/problems/7/scraps", request.url.toString())
            respond(content = "", status = HttpStatusCode.Created, headers = jsonHeaders())
        }
        val repository = KtorScrapRepository(createProblemHttpClient(engine), baseUrl)

        repository.add(problemId = 7L)
    }

    @Test
    fun remove_deletesScrapsEndpoint() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("http://test/api/problems/7/scraps", request.url.toString())
            respond(content = "", status = HttpStatusCode.NoContent, headers = jsonHeaders())
        }
        val repository = KtorScrapRepository(createProblemHttpClient(engine), baseUrl)

        repository.remove(problemId = 7L)
    }
}
