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

    // ── D2: 재시도 안내 근거(wrongAttemptCount) ────────────────────────────────

    /** 오답 재시도 안내는 서버가 원천인 wrongAttemptCount를 그대로 신뢰해야 한다. */
    @Test
    fun getToday_mapsWrongAttemptCount_onCurrentProblem() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {"sessionId":1,"sessionDate":"2026-07-14","status":"IN_PROGRESS","solvedCount":0,
                     "totalCount":5,"position":0,
                     "currentProblem":{"id":1,"question":"Q","difficulty":null,"codeSnippet":null,"wrongAttemptCount":2}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client(null, engine), baseUrl).getToday()

        assertEquals(2, session.currentProblem?.wrongAttemptCount, "재진입해도 서버가 기억한 누적 오답 수가 그대로 실린다")
    }

    /** 필드가 없는(구버전 서버 등) 응답도 그레이스풀하게 0으로 떨어진다 — 하위호환. */
    @Test
    fun getToday_withoutWrongAttemptCountField_defaultsToZero() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"sessionId":1,"sessionDate":"2026-07-14","status":"IN_PROGRESS","solvedCount":0,"totalCount":5,"position":0,"currentProblem":{"id":1,"question":"Q","difficulty":null,"codeSnippet":null}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client(null, engine), baseUrl).getToday()

        assertEquals(0, session.currentProblem?.wrongAttemptCount, "필드 부재는 하위호환으로 0")
    }

    /** POST /attempts 응답의 currentProblem도 같은 필드를 같은 방식으로 매핑한다. */
    @Test
    fun submitAttempt_mapsWrongAttemptCount_onCurrentProblem() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {"result":"MISMATCH","status":"IN_PROGRESS","solvedCount":0,"totalCount":3,"position":0,
                     "concepts":null,"explanation":null,
                     "currentProblem":{"id":1,"question":"Q","difficulty":null,"codeSnippet":null,"wrongAttemptCount":1},
                     "streak":null}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("오답")

        assertEquals(1, outcome.currentProblem?.wrongAttemptCount)
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

    // ── 난이도 조절 1차: 완료 화면 선호 난이도 제안 카드(DF1) ────────────────────

    @Test
    fun getToday_mapsNeedsDifficultyPrompt() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"sessionId":1,"sessionDate":"2026-07-21","status":"COMPLETED","solvedCount":5,"totalCount":5,"position":5,"needsDifficultyPrompt":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client("t", engine), baseUrl).getToday()

        assertTrue(session.needsDifficultyPrompt)
    }

    /** 필드가 없는(구버전 서버 등) 응답도 그레이스풀하게 false로 떨어진다 — 하위호환. */
    @Test
    fun getToday_withoutNeedsDifficultyPromptField_defaultsToFalse() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"sessionId":1,"sessionDate":"2026-07-14","status":"IN_PROGRESS","solvedCount":0,"totalCount":5,"position":0,"currentProblem":{"id":1,"question":"Q","difficulty":null,"codeSnippet":null}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client(null, engine), baseUrl).getToday()

        assertFalse(session.needsDifficultyPrompt, "필드 부재는 하위호환으로 false")
    }

    @Test
    fun submitAttempt_completing_mapsNeedsDifficultyPrompt() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"CORRECT","status":"COMPLETED","solvedCount":3,"totalCount":3,"position":3,"concepts":["스택"],"explanation":"LIFO","currentProblem":null,"streak":null,"needsDifficultyPrompt":true}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("스택")

        assertTrue(outcome.needsDifficultyPrompt)
    }

    /**
     * '조금 더 풀기'(D6·D9 일원화 — 추가 학습 폐지). 오늘 최신이 완료 상태면 새 세션을, 진행 중이면 그 세션을
     * 그대로 돌려주는 계약이라 응답 형태는 `GET /today`와 동일하다(재사용).
     */
    @Test
    fun startNextSession_postsToNextEndpoint_andMapsState() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("http://test/api/sessions/today/next", request.url.toString())
            respond(
                content = """
                    {"sessionId":2,"sessionDate":"2026-07-20","status":"IN_PROGRESS","solvedCount":0,
                     "totalCount":5,"position":0,
                     "currentProblem":{"id":11,"question":"새 세션 문제?","difficulty":"EASY","codeSnippet":null}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val session = KtorSessionRepository(client("t", engine), baseUrl).startNextSession()

        assertEquals(2, session.sessionId)
        assertEquals(SessionStatus.IN_PROGRESS, session.status)
        assertEquals(11L, session.currentProblem?.id)
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
        // 승급 필드 부재는 하위호환으로 빈 목록(DI9).
        assertTrue(outcome.newlyEligibleConcepts.isEmpty())
    }

    /** DI9: 정답 응답의 newlyEligibleConcepts가 그대로 매핑된다(승급 인라인 라인의 근거). */
    @Test
    fun submitAttempt_mapsNewlyEligibleConcepts() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"result":"CORRECT","status":"IN_PROGRESS","solvedCount":1,"totalCount":3,"position":1,"concepts":["프로세스"],"explanation":"E","currentProblem":{"id":2,"question":"다음"},"streak":null,"newlyEligibleConcepts":["프로세스"]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val outcome = KtorSessionRepository(client("t", engine), baseUrl).submitAttempt("프로세스")

        assertEquals(listOf("프로세스"), outcome.newlyEligibleConcepts)
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
