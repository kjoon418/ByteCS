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
import watson.bytecs.report.ReportCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KtorContentReportRepository가 신고를 계약대로(`POST /api/problems/{id}/reports`,
 * 본문 `{category, message}`) 보내는지 MockEngine으로 결정적으로 검증한다(team-plan.md §B [계약 v2]).
 */
class KtorContentReportRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun report_postsCategoryAndMessage_toReportsEndpoint() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/problems/7/reports", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"category\":\"WRONG_ANSWER\""), "본문에 유형 코드가 실려야 한다")
            assertTrue(body.contains("정답이 틀렸어요"), "상세 내용이 본문에 실려야 한다")
            respond(content = "", status = HttpStatusCode.Created, headers = jsonHeaders())
        }
        val repository = KtorContentReportRepository(createProblemHttpClient(engine), baseUrl)

        repository.report(problemId = 7L, category = ReportCategory.WRONG_ANSWER, message = "정답이 틀렸어요")
    }

    /** ⭐️ 상세 내용은 선택이다 — null이어도 유형만으로 전송된다. */
    @Test
    fun report_postsCategoryOnly_whenMessageIsNull() = runTest {
        val engine = MockEngine { request ->
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"category\":\"OTHER\""), "본문에 유형 코드가 실려야 한다")
            assertFalse(body.contains("\"message\":\"") , "빈 상세 내용은 문자열로 실리면 안 된다")
            respond(content = "", status = HttpStatusCode.Created, headers = jsonHeaders())
        }
        val repository = KtorContentReportRepository(createProblemHttpClient(engine), baseUrl)

        repository.report(problemId = 7L, category = ReportCategory.OTHER, message = null)
    }

    @Test
    fun report_propagatesErrorOnNon2xx() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"미지원 유형","errorCode":"REPORT_CATEGORY_UNSUPPORTED"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorContentReportRepository(createProblemHttpClient(engine), baseUrl)

        // expectSuccess=true 이므로 4xx는 예외로 올라온다(뷰모델이 전송 실패로 다룬다).
        assertFailsWith<ClientRequestException> {
            repository.report(problemId = 7L, category = ReportCategory.OTHER, message = null)
        }
    }
}
