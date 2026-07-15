package watson.bytecs.report.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import watson.bytecs.problem.data.createProblemHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * KtorContentReportRepository가 신고를 계약대로(`POST /api/problems/{id}/reports`, 본문 message)
 * 보내는지 MockEngine으로 결정적으로 검증한다.
 */
class KtorContentReportRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun report_postsMessage_toReportsEndpoint() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/problems/7/reports", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"message\""), "본문에 message 필드가 있어야 한다")
            assertTrue(body.contains("정답이 틀렸어요"), "신고 내용이 본문에 실려야 한다")
            respond(content = "", status = HttpStatusCode.Created, headers = jsonHeaders())
        }
        val repository = KtorContentReportRepository(createProblemHttpClient(engine), baseUrl)

        repository.report(problemId = 7L, message = "정답이 틀렸어요")
    }

    @Test
    fun report_propagatesErrorOnNon2xx() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"빈 신고","errorCode":"REPORT_MESSAGE_BLANK"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorContentReportRepository(createProblemHttpClient(engine), baseUrl)

        // expectSuccess=true 이므로 4xx는 예외로 올라온다(뷰모델이 전송 실패로 다룬다).
        assertFailsWith<ClientRequestException> { repository.report(problemId = 7L, message = "x") }
    }
}
