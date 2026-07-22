package watson.bytecs.account.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import watson.bytecs.account.EmailAlreadyInUseException
import watson.bytecs.account.InvalidCredentialsException
import watson.bytecs.account.InvalidInputException
import watson.bytecs.account.PreferredDifficulty
import watson.bytecs.account.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * KtorAccountRepository가 계정 API 계약대로 요청을 보내고 응답·오류를 매핑하는지 MockEngine으로 검증한다.
 * 인증 헤더는 이 저장소가 아니라 클라이언트가 붙이므로, 여기서는 토큰을 주입하는 클라이언트로 Bearer 부착을 확인한다.
 */
class KtorAccountRepositoryTest {

    private val baseUrl = "http://test"

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    /** 토큰을 항상 실어 보내는 인증 클라이언트(헤더 부착 검증용). */
    private fun authedClient(token: String?, engine: MockEngine): HttpClient =
        createAuthenticatedHttpClient(tokenProvider = { token }, engine = engine)

    @Test
    fun issueGuest_postsToGuests_andMapsResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/guests", request.url.toString())
            respond(
                content = """{"token":"guest-jwt","userId":42,"role":"GUEST"}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient(null, engine), baseUrl)

        val guest = repository.issueGuest()

        assertEquals("guest-jwt", guest.token)
        assertEquals(42L, guest.userId)
        assertEquals(Role.GUEST, guest.role)
    }

    @Test
    fun register_sendsGuestBearer_andMapsToken() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/auth/register", request.url.toString())
            // ⭐️ 게스트 토큰이 헤더에 실려야 서버가 제자리 승격한다.
            assertEquals("Bearer guest-jwt", request.headers[HttpHeaders.Authorization])
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"email\""))
            assertTrue(body.contains("\"password\""))
            respond(
                content = """{"token":"member-jwt"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("guest-jwt", engine), baseUrl)

        val session = repository.register("a@b.com", "pw12345678")

        assertEquals("member-jwt", session.token)
    }

    @Test
    fun register_maps409_toEmailAlreadyInUse() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"이미 사용 중","errorCode":"EMAIL_DUPLICATED"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("guest-jwt", engine), baseUrl)

        assertFailsWith<EmailAlreadyInUseException> { repository.register("a@b.com", "pw12345678") }
    }

    @Test
    fun register_maps400_toInvalidInput_withServerMessage() = runTest {
        // QA #5: 400은 서버 메시지를 그대로 실어 InvalidInputException으로 번역해야 한다
        // (else 폴백으로 새면 검증 실패가 연결 실패로 오인된다).
        val engine = MockEngine {
            respond(
                content = """{"message":"이메일 형식이 올바르지 않습니다.","errorCode":"INVALID_INPUT"}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("guest-jwt", engine), baseUrl)

        val exception = assertFailsWith<InvalidInputException> { repository.register("aaa@aaa", "pw12345678") }
        assertEquals("이메일 형식이 올바르지 않습니다.", exception.message)
    }

    @Test
    fun login_postsCredentials_andMapsToken() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/auth/login", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("a@b.com"))
            respond(
                content = """{"token":"member-jwt"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient(null, engine), baseUrl)

        val session = repository.login("a@b.com", "pw12345678")

        assertEquals("member-jwt", session.token)
    }

    @Test
    fun login_maps401_toInvalidCredentials() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"message":"자격 증명 실패","errorCode":"INVALID_CREDENTIALS"}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient(null, engine), baseUrl)

        assertFailsWith<InvalidCredentialsException> { repository.login("a@b.com", "wrong") }
    }

    @Test
    fun getMe_sendsBearer_andMapsMember() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("http://test/api/users/me", request.url.toString())
            assertEquals("Bearer member-jwt", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"userId":42,"role":"MEMBER","email":"a@b.com","dailySessionSize":7}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        val account = repository.getMe()

        assertEquals(42L, account.userId)
        assertEquals(Role.MEMBER, account.role)
        assertEquals("a@b.com", account.email)
        assertEquals(7, account.dailySessionSize)
        assertTrue(account.isMember)
    }

    @Test
    fun getMe_guest_hasNullEmail() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"userId":1,"role":"GUEST","email":null,"dailySessionSize":5}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("guest-jwt", engine), baseUrl)

        val account = repository.getMe()

        assertEquals(Role.GUEST, account.role)
        assertNull(account.email)
        assertTrue(!account.isMember)
    }

    @Test
    fun updateSettings_patchesBodyAndPath_andMapsResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("http://test/api/users/me/settings", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"dailySessionSize\""))
            assertTrue(body.contains("12"))
            respond(
                content = """{"userId":42,"role":"MEMBER","email":"a@b.com","dailySessionSize":12}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        val account = repository.updateSettings(12)

        assertEquals(12, account.dailySessionSize)
    }

    @Test
    fun updatePreferredDifficulty_patchesBodyAndPath_andMapsResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("http://test/api/users/me/settings", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"preferredDifficulty\""))
            assertTrue(body.contains("HARD"))
            // 세션 크기 필드는 부분 갱신 계약상 함께 보내지 않는다.
            assertTrue(!body.contains("dailySessionSize"))
            respond(
                content = """{"userId":42,"role":"MEMBER","email":"a@b.com","dailySessionSize":5,"preferredDifficulty":"HARD"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        val account = repository.updatePreferredDifficulty(PreferredDifficulty.HARD)

        assertEquals(PreferredDifficulty.HARD, account.preferredDifficulty)
    }

    @Test
    fun getMe_preferredDifficultyUnset_mapsToNull() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"userId":42,"role":"MEMBER","email":"a@b.com","dailySessionSize":5,"preferredDifficulty":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        val account = repository.getMe()

        assertNull(account.preferredDifficulty, "미설정은 null(자동 배정)로 매핑된다")
    }

    @Test
    fun dismissDifficultyPrompt_patchesBodyAndPath_andMapsResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("http://test/api/users/me/settings", request.url.toString())
            val body = (request.body as TextContent).text
            assertTrue(body.contains("\"difficultyPromptDone\""))
            assertTrue(body.contains("true"))
            // 거절은 선호를 바꾸지 않는다 — 부분 갱신 계약상 이 필드는 함께 보내지 않는다.
            assertTrue(!body.contains("preferredDifficulty"))
            respond(
                content = """{"userId":42,"role":"MEMBER","email":"a@b.com","dailySessionSize":5,"preferredDifficulty":null}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        val account = repository.dismissDifficultyPrompt()

        assertNull(account.preferredDifficulty, "거절은 선호를 미설정으로 남겨둔다")
    }

    @Test
    fun deleteMe_sendsDeleteWithBearer() = runTest {
        var seenMethod: HttpMethod? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            assertEquals("http://test/api/users/me", request.url.toString())
            assertEquals("Bearer member-jwt", request.headers[HttpHeaders.Authorization])
            respond(content = "", status = HttpStatusCode.NoContent)
        }
        val repository = KtorAccountRepository(authedClient("member-jwt", engine), baseUrl)

        repository.deleteMe()

        assertEquals(HttpMethod.Delete, seenMethod)
    }

    @Test
    fun hostScopedAuth_attachesTokenOnlyToApiHost() = runTest {
        // apiHost가 요청 호스트("test")와 일치 → 헤더 부착.
        val matchEngine = MockEngine { request ->
            assertEquals("Bearer tok", request.headers[HttpHeaders.Authorization], "API 호스트로는 토큰 부착")
            respond(
                content = """{"token":"t","userId":1,"role":"GUEST"}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }
        KtorAccountRepository(
            createAuthenticatedHttpClient(tokenProvider = { "tok" }, apiHost = "test", engine = matchEngine),
            baseUrl,
        ).issueGuest()

        // apiHost가 다른 호스트 → 헤더 미부착(다른 호스트로의 토큰 유출 방지).
        val mismatchEngine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization], "다른 호스트로는 토큰을 붙이지 않는다")
            respond(
                content = """{"token":"t","userId":1,"role":"GUEST"}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }
        KtorAccountRepository(
            createAuthenticatedHttpClient(tokenProvider = { "tok" }, apiHost = "other.example.com", engine = mismatchEngine),
            baseUrl,
        ).issueGuest()
    }

    @Test
    fun noToken_omitsAuthorizationHeader() = runTest {
        val engine = MockEngine { request ->
            assertNull(request.headers[HttpHeaders.Authorization], "토큰이 없으면 헤더를 붙이지 않는다")
            respond(
                content = """{"token":"guest-jwt","userId":1,"role":"GUEST"}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }
        val repository = KtorAccountRepository(authedClient(null, engine), baseUrl)

        repository.issueGuest()
    }
}
