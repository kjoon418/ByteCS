package watson.bytecs.session.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.hasItem
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
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
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val clock: MutableClock,
) {

    private lateinit var token: String
    private var p1: Long = 0
    private var p2: Long = 0
    private var p3: Long = 0

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val DAY1: LocalDate = LocalDate.of(2026, 7, 14)
    }

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        clock.setDate(DAY1)

        // 배정 순서(id 오름차순)를 결정적으로 만들기 위해 개념1→2→3 순서로 시드한다.
        p1 = seedProblem("개념1", "정답1", "해설1")
        p2 = seedProblem("개념2", "정답2", "해설2")
        p3 = seedProblem("개념3", "정답3", "해설3")

        token = issueTokenForNewUser()
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
            jsonPath("$.currentProblem.concept") { doesNotExist() }
            jsonPath("$.currentProblem.acceptableAnswers") { doesNotExist() }
            jsonPath("$.currentProblem.explanation") { doesNotExist() }
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
            jsonPath("$.concept") { value(nullValue()) }
            jsonPath("$.explanation") { value(nullValue()) }
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
            jsonPath("$.concept") { value("개념1") }
            jsonPath("$.explanation") { value("해설1") }
            jsonPath("$.currentProblem.id") { value(p2) }
            jsonPath("$.currentProblem.concept") { doesNotExist() }
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
            jsonPath("$.concept") { value("개념1") }
            jsonPath("$.explanation") { value("해설1") }
            jsonPath("$.acceptableAnswers") { value(hasItem("정답1")) }
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
            jsonPath("$.concept") { value("개념1") }
            jsonPath("$.acceptableAnswers") { value(hasItem("정답1")) }
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

    private fun sessionId(result: MvcResult): Long =
        objectMapper.readTree(result.response.contentAsByteArray).get("sessionId").asLong()

    private fun seedProblem(conceptName: String, answer: String, explanation: String): Long {
        val concept = conceptRepository.save(Concept(conceptName))
        val problem = problemRepository.save(
            Problem(
                questionText = "$conceptName 질문",
                concept = concept,
                acceptableAnswers = setOf(answer),
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
