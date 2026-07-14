package watson.bytecs.session.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import watson.bytecs.account.data.createAuthenticatedHttpClient
import watson.bytecs.problem.JudgeResult
import watson.bytecs.session.ItemNotViewableException
import watson.bytecs.session.RevealNotAllowedException
import watson.bytecs.session.SessionCompletedException
import watson.bytecs.session.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KtorSessionRepository가 세션 API 계약대로 요청을 보내고 응답·오류(errorCode)를 매핑하는지 MockEngine으로 검증한다.
 */
class KtorSessionRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(token: String?, engine: MockEngine): HttpClient =
        createAuthenticatedHttpClient(tokenProvider = { token }, engine = engine)

    @Test
    fun getToday_hitsEndpoint_andMapsState_withStreak() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/sessions/today", request.url.toString())
            assertEquals("Bearer guest-jwt", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {"sessionId":1,"sessionDate":"2026-07-14","status":"IN_PROGRESS","solvedCount":2,
                     "totalCount":5,"position":2,
                     "currentProblem":{"id":9,"question":"큐는?","difficulty":"EASY","codeSnippet":null},
                     "streak":{"count":3,"lastStudyDate":"2026-07-14"}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client("guest-jwt", engine), baseUrl).getToday()

        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(2, session.solvedCount)
        assertEquals(5, session.totalCount)
        assertEquals(9L, session.currentProblem?.id)
        assertEquals(3, session.streak?.count)
    }

    @Test
    fun getToday_withoutStreakField_mapsStreakNull() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"sessionId":1,"sessionDate":"2026-07-14","status":"IN_PROGRESS","solvedCount":0,"totalCount":5,"position":0,"currentProblem":{"id":1,"question":"Q","difficulty":null,"codeSnippet":null}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client(null, engine), baseUrl).getToday()
        assertNull(session.streak, "streak 필드가 없으면 null(그레이스풀)")
    }

    @Test
    fun submitAttempt_postsAnswer_andMapsCorrect() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/sessions/today/attempts", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"answer\""))
            assertTrue(body.contains("스택"))
            respond(
                content = """
                    {"result":"CORRECT","status":"IN_PROGRESS","solvedCount":1,"totalCount":3,"position":1,
                     "concept":"스택","explanation":"LIFO",
                     "currentProblem":{"id":2,"question":"다음","difficulty":null,"codeSnippet":null},"streak":null}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertEquals(JudgeResult.CORRECT, outcome.result)
        assertEquals("스택", outcome.concept)
        assertEquals(2L, outcome.currentProblem?.id)
        assertFalse(outcome.isCompleted)
    }

    @Test
    fun submitAttempt_completing_carriesStreak() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"CORRECT","status":"COMPLETED","solvedCount":3,"totalCount":3,"position":3,"concept":"스택","explanation":"LIFO","currentProblem":null,"streak":{"count":7,"lastStudyDate":"2026-07-14"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertTrue(outcome.isCompleted)
        assertEquals(7, outcome.streak?.count)
        assertNull(outcome.currentProblem)
    }

    @Test
    fun submitAttempt_alreadyCompleted_mapsToSessionCompletedException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"이미 완료","errorCode":"SESSION_ALREADY_COMPLETED"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorSessionRepository(client("t", engine), baseUrl)
        assertFailsWith<SessionCompletedException> { repo.submitAttempt("x") }
    }

    @Test
    fun reveal_postsAndMaps() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/sessions/today/reveal", request.url.toString())
            respond(
                content = """{"concept":"스택","explanation":"LIFO","acceptableAnswers":["스택","stack"]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val reveal = KtorSessionRepository(client("t", engine), baseUrl).reveal()

        assertEquals("스택", reveal.concept)
        assertEquals(listOf("스택", "stack"), reveal.acceptableAnswers)
    }

    @Test
    fun reveal_notAllowed_mapsToRevealNotAllowedException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"아직","errorCode":"REVEAL_NOT_ALLOWED"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorSessionRepository(client("t", engine), baseUrl)
        assertFailsWith<RevealNotAllowedException> { repo.reveal() }
    }

    @Test
    fun submitAttempt_serverError5xx_propagatesAsRawSystemError() = runTest {
        // errorCode 없는 5xx는 타입드 예외로 번역하지 않고 원 ResponseException으로 올린다(시스템 오류 경로).
        val engine = MockEngine {
            respond(
                content = """{"message":"서버 오류"}""",
                status = HttpStatusCode.InternalServerError,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorSessionRepository(client("t", engine), baseUrl)
        assertFailsWith<ResponseException> { repo.submitAttempt("x") }
    }

    @Test
    fun submitAttempt_unknownErrorCode_rethrowsResponseException() = runTest {
        // 매핑되지 않은 errorCode(또는 파싱 불가)는 세션 예외로 바꾸지 않고 원 예외를 그대로 올린다.
        val engine = MockEngine {
            respond(
                content = """{"message":"뭔가 다른","errorCode":"SOMETHING_ELSE"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        // ResponseException으로 떨어진다는 것 자체가 세션 타입드 예외(비-ResponseException)로 번역되지 않았음을 뜻한다.
        val repo = KtorSessionRepository(client("t", engine), baseUrl)
        assertFailsWith<ResponseException> { repo.submitAttempt("x") }
    }

    @Test
    fun getPastItem_hitsPositionPath_andMaps() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/sessions/today/items/2", request.url.toString())
            respond(
                content = """
                    {"position":2,"problemId":12,"question":"지난문제","codeSnippet":null,"difficulty":"EASY",
                     "submittedAnswer":"스택","result":"CORRECT","revealed":false,"concept":"스택","explanation":"LIFO",
                     "acceptableAnswers":["스택"]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val item = KtorSessionRepository(client("t", engine), baseUrl).getPastItem(2)

        assertEquals(2, item.position)
        assertEquals("스택", item.submittedAnswer)
        assertEquals(JudgeResult.CORRECT, item.result)
        assertEquals(listOf("스택"), item.acceptableAnswers)
    }

    @Test
    fun getPastItem_notViewable_mapsToItemNotViewableException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"아직","errorCode":"ITEM_NOT_VIEWABLE"}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorSessionRepository(client("t", engine), baseUrl)
        assertFailsWith<ItemNotViewableException> { repo.getPastItem(9) }
    }
}
