package watson.bytecs.session.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.MisconceptionHint
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@Import(SessionControllerIntegrationTest.FixedClockConfig::class)
class SessionControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val clock: MutableClock,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    private lateinit var token: String
    private var userId: Long = 0
    private var p1: Long = 0
    private var p2: Long = 0
    private var p3: Long = 0

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DAY1: LocalDate = LocalDate.of(2026, 7, 14)

        const val WEAK_HINT = "약한 힌트예요"
        const val STRONG_HINT = "강한 힌트예요"
        const val MISCONCEPTION_ANSWER = "흔한오답"
        const val MISCONCEPTION_MESSAGE = "그건 다른 개념이에요. 다시 도전해보세요!"
        const val ENRICHMENT = "심화 정보예요"
    }

    @BeforeEach
    fun setUp() {
        conceptMasteryRepository.deleteAll()
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        clock.setDate(DAY1)

        // 배정 순서(id 오름차순)를 결정적으로 만들기 위해 개념1→2→3 순서로 시드한다.
        p1 = seedProblem("개념1", "정답1", "해설1")
        p2 = seedProblem("개념2", "정답2", "해설2")
        p3 = seedProblem("개념3", "정답3", "해설3")

        val user = userRepository.save(User.createGuest())
        userId = user.id
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `오늘 세션을 처음 조회하면 진행중 세션을 만들고 현재 문제를 노낙인으로 준다`() {
        getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_PROGRESS") }
            jsonPath("$.position") { value(0) }
            jsonPath("$.solvedCount") { value(0) }
            jsonPath("$.totalCount") { value(3) }
            jsonPath("$.currentProblem.id") { value(p1) }
            jsonPath("$.currentProblem.question") { exists() }
            // 정답을 유추할 수 있는 정보는 새어 나가지 않아야 한다.
            jsonPath("$.currentProblem.concepts") { doesNotExist() }
            jsonPath("$.currentProblem.acceptableAnswers") { doesNotExist() }
            jsonPath("$.currentProblem.representativeAnswer") { doesNotExist() }
            jsonPath("$.currentProblem.explanation") { doesNotExist() }
            jsonPath("$.currentProblem.enrichment") { doesNotExist() }
        }
    }

    @Test
    fun `오늘 세션을 두 번 조회해도 같은 세션이며 중복 생성되지 않는다`() {
        val first = getToday(token).andReturn()
        val second = getToday(token).andReturn()

        assertThat(sessionId(first)).isEqualTo(sessionId(second))
        assertThat(sessionRepository.count()).isEqualTo(1)
    }

    @Test
    fun `오답을 제출하면 200이며 전진하지 않고 개념을 노출하지 않는다`() {
        getToday(token)

        submit(token, "완전히 다른 답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.position") { value(0) }
            jsonPath("$.solvedCount") { value(0) }
            jsonPath("$.concepts") { value(nullValue()) }
            jsonPath("$.explanation") { value(nullValue()) }
            jsonPath("$.representativeAnswer") { value(nullValue()) }
            jsonPath("$.currentProblem.id") { value(p1) }
        }
    }

    @Test
    fun `정답을 제출하면 전진하고 개념과 해설을 공개하며 다음 문제를 노낙인으로 준다`() {
        getToday(token)

        submit(token, "정답1").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.solvedCount") { value(1) }
            jsonPath("$.position") { value(1) }
            jsonPath("$.concepts[0]") { value("개념1") }
            jsonPath("$.explanation") { value("해설1") }
            // 정답이므로 대표 정답이 실린다(seedProblem은 대표 정답을 정답 문자열로 둔다).
            jsonPath("$.representativeAnswer") { value("정답1") }
            // 심화 정보가 없는 문제는 정답이어도 null이다(graceful).
            jsonPath("$.enrichment") { value(nullValue()) }
            jsonPath("$.currentProblem.id") { value(p2) }
            jsonPath("$.currentProblem.concepts") { doesNotExist() }
            jsonPath("$.currentProblem.representativeAnswer") { doesNotExist() }
            jsonPath("$.currentProblem.enrichment") { doesNotExist() }
        }
    }

    @Test
    fun `심화 정보가 있는 문제를 정답 처리하면 함께 공개된다`() {
        reseedEnrichedProblem()
        getToday(token)

        submit(token, "정답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.enrichment") { value(ENRICHMENT) }
        }
    }

    @Test
    fun `오답이면 심화 정보가 있는 문제라도 노출되지 않는다`() {
        reseedEnrichedProblem()
        getToday(token)

        submit(token, "완전히 다른 답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.enrichment") { value(nullValue()) }
        }
    }

    @Test
    fun `복수 개념 문제를 정답 처리하면 개념을 태깅 순서대로 배열로 공개한다`() {
        reseedMultiConceptProblem()
        getToday(token)

        submit(token, "정답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            // 대표 개념이 먼저, 그다음 태깅 순서. 단수 문자열이 아니라 배열로 내려간다.
            jsonPath("$.concepts.length()") { value(2) }
            jsonPath("$.concepts[0]") { value("대표개념") }
            jsonPath("$.concepts[1]") { value("보조개념") }
        }
    }

    @Test
    fun `시도 전에는 정답 공개가 막히고 한 번 틀린 뒤에는 공개된다`() {
        getToday(token)

        // 아직 한 번도 시도하지 않았으므로 공개 불가.
        reveal(token).andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("REVEAL_NOT_ALLOWED") }
        }

        submit(token, "틀린 답")

        reveal(token).andExpect {
            status { isOk() }
            jsonPath("$.concepts[0]") { value("개념1") }
            jsonPath("$.explanation") { value("해설1") }
            jsonPath("$.representativeAnswer") { value("정답1") }
            // 심화 정보가 없는 문제는 정답 공개에서도 null이다(graceful).
            jsonPath("$.enrichment") { value(nullValue()) }
        }

        // 공개 후에도 직접 정답을 입력해야 전진한다.
        submit(token, "정답1").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.position") { value(1) }
        }
    }

    @Test
    fun `모든 문제를 통과하면 완료되고 스트릭이 1이 된다`() {
        getToday(token)

        submit(token, "정답1")
        submit(token, "정답2")
        submit(token, "정답3").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.solvedCount") { value(3) }
            jsonPath("$.currentProblem") { value(nullValue()) }
            jsonPath("$.streak.count") { value(1) }
            jsonPath("$.streak.lastStudyDate") { value(DAY1.toString()) }
        }
    }

    @Test
    fun `연속으로 완료하면 스트릭이 오르고 하루 건너뛰면 리셋된다`() {
        // Day1 완료 → 스트릭 1
        completeAllThree(token)

        // Day2 완료 → 연속이라 스트릭 2 (새 개념 소진 후 전체 풀 폴백 배정)
        clock.setDate(DAY1.plusDays(1))
        completeAllThree(token).andExpect {
            jsonPath("$.streak.count") { value(2) }
        }

        // Day4로 건너뛰면 → 스트릭 1로 리셋
        clock.setDate(DAY1.plusDays(3))
        completeAllThree(token).andExpect {
            jsonPath("$.streak.count") { value(1) }
        }
    }

    @Test
    fun `지난 문제는 통과한 위치만 조회되고 현재 위치는 볼 수 없다`() {
        getToday(token)
        submit(token, "정답1")

        // 통과한 위치 0은 내가 쓴 답·모범답안까지 보인다.
        mockMvc.get("/api/sessions/today/items/0") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.position") { value(0) }
            jsonPath("$.problemId") { value(p1) }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.submittedAnswer") { value("정답1") }
            jsonPath("$.concepts[0]") { value("개념1") }
            jsonPath("$.representativeAnswer") { value("정답1") }
            // 심화 정보가 없는 문제는 지난 문제 다시 보기에서도 null이다(graceful).
            jsonPath("$.enrichment") { value(nullValue()) }
        }

        // 아직 도달하지 않은 현재 위치 1은 노출하지 않는다(no-leak).
        mockMvc.get("/api/sessions/today/items/1") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.errorCode") { value("ITEM_NOT_VIEWABLE") }
        }
    }

    @Test
    fun `인증 없이 오늘 세션을 조회하면 401을 반환한다`() {
        mockMvc.get("/api/sessions/today")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `사용자마다 세션이 격리되어 서로의 세션을 보지 않는다`() {
        val aResult = getToday(token).andReturn()

        val otherToken = issueTokenForNewUser()
        val bResult = getToday(otherToken).andReturn()

        assertThat(sessionId(aResult)).isNotEqualTo(sessionId(bResult))
        assertThat(sessionRepository.count()).isEqualTo(2)
    }

    @Test
    fun `이미 통과한 문제는 이후 세션 배정에서 제외된다`() {
        // Day1: p1만 통과하고 세션은 미완료로 남긴다.
        getToday(token)
        submit(token, "정답1")

        // Day2: 새 세션은 이미 통과한 p1을 빼고 배정한다.
        clock.setDate(DAY1.plusDays(1))
        val result = getToday(token).andReturn()
        val tree = objectMapper.readTree(result.response.contentAsByteArray)

        assertThat(tree.get("totalCount").asInt()).isEqualTo(2)
        assertThat(tree.get("currentProblem").get("id").asLong()).isEqualTo(p2)
    }

    @Test
    fun `문제가 하나도 없으면 오늘 세션 조회는 404를 반환한다`() {
        problemRepository.deleteAll()

        getToday(token).andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("PROBLEM_NOT_FOUND") }
        }
    }

    @Test
    fun `완료된 세션에 답을 제출하면 409를 반환한다`() {
        completeAllThree(token)

        submit(token, "아무 답").andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("SESSION_ALREADY_COMPLETED") }
        }
    }

    @Test
    fun `완료된 세션에서 정답 공개를 요청하면 409를 반환한다`() {
        completeAllThree(token)

        reveal(token).andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("SESSION_ALREADY_COMPLETED") }
        }
    }

    @Test
    fun `오늘 세션 조회는 현재 스트릭을 함께 준다`() {
        // 새 사용자는 스트릭 0으로 시작한다(홈 화면이 로드 시점에 바로 보여줄 수 있어야 한다).
        getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.streak.count") { value(0) }
        }

        // Day1을 완료하면 스트릭이 1이 되고, 다음 날 조회에 그대로 반영된다.
        completeAllThree(token)
        clock.setDate(DAY1.plusDays(1))
        getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.streak.count") { value(1) }
            jsonPath("$.streak.lastStudyDate") { value(DAY1.toString()) }
        }
    }

    @Test
    fun `힌트가 없는 문제는 hintCount 0과 빈 공개목록을 주고 진입점 판단을 클라에 맡긴다`() {
        getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.currentProblem.hintCount") { value(0) }
            jsonPath("$.currentProblem.revealedHints") { isEmpty() }
        }
    }

    @Test
    fun `힌트 개수는 알리되 미공개 힌트 본문은 오늘 세션 응답에 새지 않는다`() {
        reseedSingleHintedProblem()

        val result = getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.currentProblem.hintCount") { value(2) }
            jsonPath("$.currentProblem.revealedHints") { isEmpty() }
        }.andReturn()

        // 아직 아무 힌트도 열지 않았으므로 약한 힌트조차 본문이 실려선 안 된다(no-leak).
        assertThat(bodyOf(result)).doesNotContain(WEAK_HINT).doesNotContain(STRONG_HINT)
    }

    @Test
    fun `힌트를 열면 약한 것부터 하나씩 공개되고 강한 힌트는 아직 새지 않는다`() {
        reseedSingleHintedProblem()
        getToday(token)

        val result = revealHint(token, 0).andExpect {
            status { isOk() }
            jsonPath("$.hintCount") { value(2) }
            jsonPath("$.revealedHints.length()") { value(1) }
            jsonPath("$.revealedHints[0].text") { value(WEAK_HINT) }
        }.andReturn()

        // 한 개만 열었으므로 강한 힌트 본문은 여전히 새지 않는다.
        assertThat(bodyOf(result)).doesNotContain(STRONG_HINT)
    }

    @Test
    fun `이미 연 힌트는 재진입한 오늘 세션 응답에 복원된다`() {
        reseedSingleHintedProblem()
        getToday(token)
        revealHint(token, 0)

        getToday(token).andExpect {
            status { isOk() }
            jsonPath("$.currentProblem.revealedHints.length()") { value(1) }
            jsonPath("$.currentProblem.revealedHints[0].text") { value(WEAK_HINT) }
        }
    }

    @Test
    fun `공개 수가 어긋난 요청은 더블탭에도 힌트를 더 열지 않는다`() {
        reseedSingleHintedProblem()
        getToday(token)
        revealHint(token, 0) // 이제 1개 공개.

        // 클라가 여전히 0을 들고 다시 누르면(더블탭) 증가하지 않는다.
        revealHint(token, 0).andExpect {
            status { isOk() }
            jsonPath("$.revealedHints.length()") { value(1) }
        }
    }

    @Test
    fun `모든 힌트를 연 뒤 더 열려 해도 증가하지 않는다`() {
        reseedSingleHintedProblem()
        getToday(token)
        revealHint(token, 0)
        revealHint(token, 1)

        revealHint(token, 2).andExpect {
            status { isOk() }
            jsonPath("$.revealedHints.length()") { value(2) }
        }
    }

    @Test
    fun `예상 오답을 제출하면 교정 힌트가 뜨고 오답으로 확정되지 않는다`() {
        reseedSingleHintedProblem()
        getToday(token)

        submit(token, MISCONCEPTION_ANSWER).andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.misconceptionHint") { value(MISCONCEPTION_MESSAGE) }
            // 무낙인: 전진하지 않고 정답·개념을 노출하지 않는다.
            jsonPath("$.position") { value(0) }
            jsonPath("$.concepts") { value(nullValue()) }
        }
    }

    @Test
    fun `예상 밖 오답은 교정 힌트 없이 일반 불일치로 흐른다`() {
        reseedSingleHintedProblem()
        getToday(token)

        submit(token, "전혀 다른 오답").andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.misconceptionHint") { value(nullValue()) }
        }
    }

    @Test
    fun `완료된 세션에서 힌트를 열려 하면 409를 반환한다`() {
        completeAllThree(token)

        revealHint(token, 0).andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("SESSION_ALREADY_COMPLETED") }
        }
    }

    @Test
    fun `인증 없이 힌트를 열면 401을 반환한다`() {
        mockMvc.post("/api/sessions/today/hints/reveal") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"revealedCount":0}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `무도움으로 통과하면 그 문제의 모든 개념 숙련도가 생기고 복습이 3일 뒤가 된다`() {
        reseedMultiConceptProblem()
        getToday(token)

        submit(token, "정답").andExpect { status { isOk() } }

        // 힌트·정답 공개·교정 힌트 없이 맞힘 → 강한 정착(레벨 1, 사다리[1]=3일). 두 개념 모두에 적용된다.
        listOf("대표개념", "보조개념").forEach { conceptName ->
            val mastery = masteryOf(conceptName)
            assertThat(mastery.level).isEqualTo(1)
            assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(3))
        }
    }

    @Test
    fun `힌트를 열고 맞히면 약한 정착으로 복습이 1일 뒤가 된다`() {
        reseedSingleHintedProblem()
        getToday(token)
        revealHint(token, 0)

        submit(token, "정답").andExpect { status { isOk() } }

        // 도움받아 맞힘 → 레벨 유지(0), 사다리[0]=1일.
        val mastery = masteryOf("힌트개념")
        assertThat(mastery.level).isEqualTo(0)
        assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(1))
    }

    @Test
    fun `정답 공개를 쓴 뒤 맞히면 숙련도가 내려가 복습이 1일 뒤가 된다`() {
        reseedSingleHintedProblem()
        getToday(token)
        submit(token, "틀린 답")
        reveal(token)

        submit(token, "정답").andExpect { status { isOk() } }

        // 정답 공개(포기) → 레벨 하락(0에서 바닥), 사다리[0]=1일.
        val mastery = masteryOf("힌트개념")
        assertThat(mastery.level).isEqualTo(0)
        assertThat(mastery.nextReviewDate).isEqualTo(DAY1.plusDays(1))
    }

    @Test
    fun `복습 시점이 도래한 개념의 문제가 다음 세션에 편입된다`() {
        // 개념3을 5일 전에 무도움으로 통과한 것으로 두면 복습 시점(3일 뒤 = 2일 전)이 오늘 도래해 있다.
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(userId, conceptIdOf("개념3"), MasterySignal.UNAIDED, DAY1.minusDays(5), p3),
        )

        // 자연 배정이면 p1이 먼저지만, 도래 복습 p3가 우선 편입되어 현재 문제가 된다.
        val result = getToday(token).andReturn()
        val tree = objectMapper.readTree(result.response.contentAsByteArray)

        assertThat(tree.get("currentProblem").get("id").asLong()).isEqualTo(p3)
        assertThat(tree.get("totalCount").asInt()).isEqualTo(3)
    }

    @Test
    fun `계정을 삭제하면 그 사용자의 개념 숙련도도 함께 삭제된다`() {
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(userId, conceptIdOf("개념1"), MasterySignal.UNAIDED, DAY1, p1),
        )
        assertThat(conceptMasteryRepository.count()).isEqualTo(1)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertThat(conceptMasteryRepository.count()).isEqualTo(0)
    }

    @Test
    fun `계정을 삭제하면 그 사용자의 세션과 세션 항목도 함께 삭제된다`() {
        getToday(token)
        submit(token, "정답1")
        assertThat(sessionRepository.count()).isEqualTo(1)
        assertThat(sessionItemRowCount()).isEqualTo(3)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertThat(sessionRepository.count()).isEqualTo(0)
        // 벌크 JPQL delete로 바뀌면 세션 행은 지워져도 @ElementCollection 테이블(study_session_item)이
        // 남아 고아 행이 생긴다. 이 단언이 파생 쿼리(deleteByUserId) 사용을 강제하는 가드다.
        assertThat(sessionItemRowCount()).isEqualTo(0)
    }

    @Test
    fun `계정을 삭제해도 다른 사용자의 세션은 영향받지 않는다`() {
        getToday(token)
        val otherToken = issueTokenForNewUser()
        getToday(otherToken)
        assertThat(sessionRepository.count()).isEqualTo(2)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertThat(sessionRepository.count()).isEqualTo(1)
    }

    private fun sessionItemRowCount(): Long =
        jdbcTemplate.queryForObject("select count(*) from study_session_item", Long::class.java)!!

    private fun conceptIdOf(conceptName: String): Long =
        conceptRepository.findAll().first { it.name == conceptName }.id

    private fun masteryOf(conceptName: String): ConceptMastery =
        conceptMasteryRepository.findByUserIdAndConceptId(userId, conceptIdOf(conceptName))
            ?: error("개념 '$conceptName'의 숙련도가 없습니다.")

    private fun completeAllThree(bearer: String): ResultActionsDsl {
        getToday(bearer)
        submit(bearer, "정답1")
        submit(bearer, "정답2")
        return submit(bearer, "정답3")
    }

    private fun getToday(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/sessions/today") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun submit(bearer: String, answer: String): ResultActionsDsl =
        mockMvc.post("/api/sessions/today/attempts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"$answer"}"""
        }

    private fun reveal(bearer: String): ResultActionsDsl =
        mockMvc.post("/api/sessions/today/reveal") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun revealHint(bearer: String, revealedCount: Int): ResultActionsDsl =
        mockMvc.post("/api/sessions/today/hints/reveal") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"revealedCount":$revealedCount}"""
        }

    private fun bodyOf(result: MvcResult): String =
        result.response.contentAsByteArray.toString(Charsets.UTF_8)

    /**
     * 기존 시드(p1~p3)를 지우고, 힌트 2개(약→강)와 오답 교정 힌트 1개를 가진 문제 하나만 남긴다.
     * 세션은 첫 getToday에서 지연 생성되므로, 이 재시드는 세션 생성 전에만 유효하다.
     */
    private fun reseedSingleHintedProblem() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        val concept = conceptRepository.save(Concept("힌트개념"))
        problemRepository.save(
            Problem(
                questionText = "힌트 문제",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
                hints = listOf(Hint(WEAK_HINT), Hint(STRONG_HINT)),
                misconceptionHints = listOf(
                    MisconceptionHint(setOf(MISCONCEPTION_ANSWER), MISCONCEPTION_MESSAGE),
                ),
            ),
        )
    }

    /**
     * 기존 시드(p1~p3)를 지우고, 두 개념(대표·보조 순)을 태깅한 문제 하나만 남긴다.
     * 개념 노출 응답이 단수 문자열이 아니라 태깅 순 배열로 내려가는지 검증하는 데 쓴다.
     */
    private fun reseedMultiConceptProblem() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        val primary = conceptRepository.save(Concept("대표개념"))
        val secondary = conceptRepository.save(Concept("보조개념"))
        problemRepository.save(
            Problem(
                questionText = "복수 개념 문제",
                concepts = listOf(primary, secondary),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
            ),
        )
    }

    /**
     * 기존 시드(p1~p3)를 지우고, 심화 정보를 가진 문제 하나만 남긴다.
     * 정답 처리 후에만 심화 정보가 노출되는지 검증하는 데 쓴다.
     */
    private fun reseedEnrichedProblem() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        val concept = conceptRepository.save(Concept("심화개념"))
        problemRepository.save(
            Problem(
                questionText = "심화 정보 문제",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
                enrichment = ENRICHMENT,
            ),
        )
    }

    private fun sessionId(result: MvcResult): Long =
        objectMapper.readTree(result.response.contentAsByteArray).get("sessionId").asLong()

    private fun seedProblem(conceptName: String, answer: String, explanation: String): Long {
        val concept = conceptRepository.save(Concept(conceptName))
        val problem = problemRepository.save(
            Problem(
                questionText = "$conceptName 질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf(answer),
                representativeAnswer = answer,
                difficulty = Difficulty.EASY,
                explanation = explanation,
            ),
        )
        return problem.id
    }

    private fun issueTokenForNewUser(): String {
        val user = userRepository.save(User.createGuest())
        return jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    /**
     * 날짜를 테스트에서 결정적으로 고정·전진시키는 시계.
     * KST 자정 순간을 담아, LocalDate.now(clock)이 원하는 날짜를 돌려주게 한다.
     */
    class MutableClock(private var current: Instant, private val zone: ZoneId) : Clock() {
        fun setDate(date: LocalDate) {
            current = date.atStartOfDay(zone).toInstant()
        }

        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
        override fun instant(): Instant = current
    }

    @TestConfiguration
    class FixedClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock =
            MutableClock(DAY1.atStartOfDay(KST).toInstant(), KST)
    }
}
