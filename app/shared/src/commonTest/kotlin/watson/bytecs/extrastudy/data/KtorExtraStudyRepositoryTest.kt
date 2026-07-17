package watson.bytecs.extrastudy.data

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
import watson.bytecs.extrastudy.ExtraStudyNoOpenItemException
import watson.bytecs.extrastudy.ExtraStudyState
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.EnrichmentItem
import watson.bytecs.problem.JudgeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KtorExtraStudyRepository가 추가 학습 API 계약대로 요청을 보내고 응답·오류(errorCode)를 매핑하는지 MockEngine으로 검증한다.
 */
class KtorExtraStudyRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(token: String?, engine: MockEngine): HttpClient =
        createAuthenticatedHttpClient(tokenProvider = { token }, engine = engine)

    @Test
    fun getCurrent_hitsEndpoint_andMapsAvailable() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/extra-study/current", request.url.toString())
            assertEquals("Bearer guest-jwt", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {"exhausted":false,
                     "problem":{"id":12,"question":"큐는?","difficulty":"EASY","codeSnippet":null,
                       "hintCount":2,"revealedHints":[{"text":"이미 본 힌트","codeSnippet":null}]}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val state = KtorExtraStudyRepository(client("guest-jwt", engine), baseUrl).getCurrent()

        assertTrue(state is ExtraStudyState.Available)
        assertEquals(12L, state.problem.id)
        assertEquals(2, state.problem.hintCount)
        assertEquals(1, state.problem.revealedHints.size)
        assertEquals("이미 본 힌트", state.problem.revealedHints.first().text)
    }

    @Test
    fun getCurrent_exhausted_mapsToExhausted() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"exhausted":true,"problem":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val state = KtorExtraStudyRepository(client(null, engine), baseUrl).getCurrent()
        assertEquals(ExtraStudyState.Exhausted, state)
    }

    @Test
    fun submitAttempt_postsAnswer_andMapsCorrect() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/extra-study/attempts", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"answer\""))
            assertTrue(body.contains("스택"))
            respond(
                content = """
                    {"result":"CORRECT","concepts":["스택"],"explanation":"LIFO",
                     "enrichment":{"title":"스택의 쓰임","body":"스택은 함수 호출 스택에도 쓰여요.",
                       "items":[{"title":"콜 스택","description":"함수 호출 순서를 스택으로 관리해요."}],
                       "quote":"LIFO는 어디에나 있다."},
                     "representativeAnswer":"스택 (stack)"}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorExtraStudyRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertEquals(JudgeResult.CORRECT, outcome.result)
        assertEquals(listOf("스택"), outcome.concepts)
        assertEquals("스택 (stack)", outcome.representativeAnswer)
        assertEquals(
            Enrichment(
                title = "스택의 쓰임",
                body = "스택은 함수 호출 스택에도 쓰여요.",
                items = listOf(EnrichmentItem(title = "콜 스택", description = "함수 호출 순서를 스택으로 관리해요.")),
                quote = "LIFO는 어디에나 있다.",
            ),
            outcome.enrichment,
        )
    }

    @Test
    fun submitAttempt_mismatch_carriesMisconceptionHint() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"MISMATCH","misconceptionHint":"실행 흐름의 단위를 다시 떠올려 봐요"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorExtraStudyRepository(client("t", engine), baseUrl).submitAttempt("프로세스")

        assertEquals(JudgeResult.MISMATCH, outcome.result)
        assertEquals("실행 흐름의 단위를 다시 떠올려 봐요", outcome.misconceptionHint)
        // 무낙인·no-leak: 비정답엔 개념·해설·정답이 없다.
        assertNull(outcome.concepts)
        assertNull(outcome.explanation)
        assertNull(outcome.representativeAnswer)
    }

    @Test
    fun submitAttempt_noOpenItem_mapsToTypedException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"열린 항목 없음","errorCode":"EXTRA_STUDY_NO_OPEN_ITEM"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorExtraStudyRepository(client("t", engine), baseUrl)
        assertFailsWith<ExtraStudyNoOpenItemException> { repo.submitAttempt("x") }
    }

    @Test
    fun reveal_postsAndMaps() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/extra-study/reveal", request.url.toString())
            respond(
                content = """
                    {"concepts":["스택","자료구조"],"explanation":"LIFO","representativeAnswer":"스택 (stack)",
                     "enrichment":{"title":"스택의 쓰임","body":"스택은 함수 호출 스택에도 쓰여요."}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val reveal = KtorExtraStudyRepository(client("t", engine), baseUrl).reveal()

        assertEquals(listOf("스택", "자료구조"), reveal.concepts)
        assertEquals("스택 (stack)", reveal.representativeAnswer)
        assertEquals(Enrichment(title = "스택의 쓰임", body = "스택은 함수 호출 스택에도 쓰여요."), reveal.enrichment)
    }

    @Test
    fun reveal_noOpenItem_mapsToTypedException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"열린 항목 없음","errorCode":"EXTRA_STUDY_NO_OPEN_ITEM"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorExtraStudyRepository(client("t", engine), baseUrl)
        assertFailsWith<ExtraStudyNoOpenItemException> { repo.reveal() }
    }

    @Test
    fun revealHint_postsRevealedCount_andMaps() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/extra-study/hints/reveal", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"revealedCount\""))
            assertTrue(body.contains("1"))
            respond(
                content = """{"hintCount":2,"revealedHints":[{"text":"첫 힌트","codeSnippet":null},{"text":"둘째 힌트","codeSnippet":null}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val result = KtorExtraStudyRepository(client("t", engine), baseUrl).revealHint(1)

        assertEquals(2, result.hintCount)
        assertEquals(2, result.revealedHints.size)
        assertEquals("첫 힌트", result.revealedHints.first().text)
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
        val repo = KtorExtraStudyRepository(client("t", engine), baseUrl)
        assertFailsWith<ResponseException> { repo.submitAttempt("x") }
    }

    @Test
    fun submitAttempt_unknownErrorCode_rethrowsResponseException() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"뭔가 다른","errorCode":"SOMETHING_ELSE"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repo = KtorExtraStudyRepository(client("t", engine), baseUrl)
        assertFailsWith<ResponseException> { repo.submitAttempt("x") }
    }
}
