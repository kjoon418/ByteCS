package watson.bytecs.problem.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import watson.bytecs.problem.JudgeResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KtorProblemRepository가 실제 API 계약대로 요청을 보내고 응답을 도메인으로 매핑하는지
 * MockEngine으로 결정적으로 검증한다(네트워크·서버 없이 클라이언트 계약을 고정).
 */
class KtorProblemRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun getNext_hitsNextEndpoint_andMapsResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/problems/next", request.url.toString())
            respond(
                content = """{"id":7,"question":"큐는?","difficulty":"EASY","codeSnippet":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        val problem = repository.getNext()

        assertEquals(7L, problem.id)
        assertEquals("큐는?", problem.question)
        assertEquals("EASY", problem.difficulty)
        assertNull(problem.codeSnippet)
    }

    @Test
    fun submitAttempt_postsAnswer_andMapsCorrectWithConcept() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/problems/7/attempts", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"answer\""), "본문에 answer 필드가 있어야 한다")
            assertTrue(body.contains("스택"), "제출한 답이 본문에 실려야 한다")
            respond(
                content = """{"result":"CORRECT","concepts":["스택"],"explanation":"LIFO 구조"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        val result = repository.submitAttempt(problemId = 7L, answer = "스택")

        assertEquals(JudgeResult.CORRECT, result.result)
        assertEquals(listOf("스택"), result.concepts)
        assertEquals("LIFO 구조", result.explanation)
    }

    @Test
    fun submitAttempt_mapsMismatch_withoutConceptLeak() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"MISMATCH","concepts":null,"explanation":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        val result = repository.submitAttempt(problemId = 7L, answer = "틀린답")

        assertEquals(JudgeResult.MISMATCH, result.result)
        assertNull(result.concepts)
        assertNull(result.explanation)
    }

    @Test
    fun submitAttempt_mapsNearMiss_withoutConceptLeak() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"NEAR_MISS"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        val result = repository.submitAttempt(problemId = 7L, answer = "스텍")

        assertEquals(JudgeResult.NEAR_MISS, result.result)
        assertNull(result.concepts)
        assertNull(result.explanation)
    }

    @Test
    fun submitAttempt_mapsResultCaseInsensitively() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"correct","concepts":["스택"],"explanation":"LIFO"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        val result = repository.submitAttempt(problemId = 7L, answer = "스택")

        assertEquals(JudgeResult.CORRECT, result.result)
        assertEquals(listOf("스택"), result.concepts)
    }

    @Test
    fun submitAttempt_unknownResult_throwsDistinctError() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"WHAT_IS_THIS"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        // 미지 판정값은 명확한 예외로 → 뷰모델이 submitFailed로 친절하게 처리(네트워크 오류로 오인 안 함).
        assertFailsWith<IllegalStateException> { repository.submitAttempt(problemId = 7L, answer = "x") }
    }

    @Test
    fun getNext_propagatesErrorOnNon2xx() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"문제가 없습니다.","errorCode":"PROBLEM_NOT_FOUND"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorProblemRepository(createProblemHttpClient(engine), baseUrl)

        // expectSuccess=true 이므로 4xx는 예외로 올라온다(뷰모델이 오류 상태로 다룬다).
        assertFailsWith<ClientRequestException> { repository.getNext() }
    }
}
