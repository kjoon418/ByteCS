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
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.EnrichmentItem
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
                     "concepts":["스택"],"explanation":"LIFO",
                     "enrichment":{"title":"스택의 쓰임","body":"스택은 함수 호출 스택에도 쓰여요.",
                       "items":[{"title":"콜 스택","description":"함수 호출 순서를 스택으로 관리해요."}],
                       "quote":"LIFO는 어디에나 있다."},
                     "representativeAnswer":"스택 (stack)",
                     "currentProblem":{"id":2,"question":"다음","difficulty":null,"codeSnippet":null},"streak":null}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertEquals(JudgeResult.CORRECT, outcome.result)
        assertEquals(listOf("스택"), outcome.concepts)
        assertEquals(2L, outcome.currentProblem?.id)
        assertFalse(outcome.isCompleted)
        // '더 알아보기'(§5.7) 구조체(제목·리드·항목·인용)가 서버 응답에서 그대로 매핑된다(계약 §B).
        assertEquals(
            Enrichment(
                title = "스택의 쓰임",
                body = "스택은 함수 호출 스택에도 쓰여요.",
                items = listOf(EnrichmentItem(title = "콜 스택", description = "함수 호출 순서를 스택으로 관리해요.")),
                quote = "LIFO는 어디에나 있다.",
            ),
            outcome.enrichment,
        )
        // 화면 표시용 대표 정답도 CORRECT 응답에서 그대로 매핑된다.
        assertEquals("스택 (stack)", outcome.representativeAnswer)
    }

    @Test
    fun submitAttempt_completing_carriesStreak() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"CORRECT","status":"COMPLETED","solvedCount":3,"totalCount":3,"position":3,"concepts":["스택"],"explanation":"LIFO","currentProblem":null,"streak":{"count":7,"lastStudyDate":"2026-07-14"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertTrue(outcome.isCompleted)
        assertEquals(7, outcome.streak?.count)
        assertNull(outcome.currentProblem)
        // enrichment가 응답에 없으면 그레이스풀하게 null.
        assertNull(outcome.enrichment)
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
                content = """
                    {"concepts":["스택","자료구조"],"explanation":"LIFO","representativeAnswer":"스택 (stack)",
                     "enrichment":{"title":"스택의 쓰임","body":"스택은 함수 호출 스택에도 쓰여요."}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val reveal = KtorSessionRepository(client("t", engine), baseUrl).reveal()

        // 태깅 순서 보존 확인(첫 번째 = 대표 개념) + 복수 개념 매핑.
        assertEquals(listOf("스택", "자료구조"), reveal.concepts)
        // 화면 표시용 대표 정답 하나(허용답 나열 없음, [2026-07-16] 오너 결정).
        assertEquals("스택 (stack)", reveal.representativeAnswer)
        // 정답 공개도 정답 접근 허용 맥락이라 '더 알아보기' 구조체가 포함된다(항목·인용 없는 부분 구조).
        assertEquals(Enrichment(title = "스택의 쓰임", body = "스택은 함수 호출 스택에도 쓰여요."), reveal.enrichment)
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
                     "submittedAnswer":"스택","result":"CORRECT","revealed":false,"concepts":["스택"],"explanation":"LIFO",
                     "representativeAnswer":"스택 (stack)",
                     "enrichment":{"title":"스택의 쓰임","body":"스택은 함수 호출 스택에도 쓰여요."}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val item = KtorSessionRepository(client("t", engine), baseUrl).getPastItem(2)

        assertEquals(2, item.position)
        assertEquals("스택", item.submittedAnswer)
        assertEquals(JudgeResult.CORRECT, item.result)
        // 화면 표시용 대표 정답 하나(허용답 나열 없음, [2026-07-16] 오너 결정).
        assertEquals("스택 (stack)", item.representativeAnswer)
        // 지난 문제 다시 보기도 정답 접근 허용 맥락이라 '더 알아보기' 구조체가 포함된다.
        assertEquals(Enrichment(title = "스택의 쓰임", body = "스택은 함수 호출 스택에도 쓰여요."), item.enrichment)
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
