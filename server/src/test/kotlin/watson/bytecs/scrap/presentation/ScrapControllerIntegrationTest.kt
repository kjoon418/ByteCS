package watson.bytecs.scrap.presentation

import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
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
import watson.bytecs.scrap.infrastructure.ScrapRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@SpringBootTest
@AutoConfigureMockMvc
@Import(ScrapControllerIntegrationTest.FixedClockConfig::class)
class ScrapControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val scrapRepository: ScrapRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val clock: MutableClock,
) {

    private lateinit var token: String
    private var userId: Long = 0
    private var p1: Long = 0
    private var p2: Long = 0

    @BeforeEach
    fun setUp() {
        scrapRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
        clock.reset()

        p1 = seedProblem("스택", "스택")
        p2 = seedProblem("큐", "큐")

        val user = userRepository.save(User.createGuest())
        userId = user.id
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `문제를 스크랩하면 201이고 저장된다`() {
        scrap(token, p1).andExpect { status { isCreated() } }

        assertThat(scrapRepository.existsByUserIdAndProblemId(userId, p1)).isTrue()
    }

    @Test
    fun `같은 문제를 두 번 스크랩해도 멱등하게 하나만 저장된다`() {
        scrap(token, p1).andExpect { status { isCreated() } }
        scrap(token, p1).andExpect { status { isCreated() } }

        assertThat(scrapRepository.count()).isEqualTo(1)
    }

    @Test
    fun `없는 문제를 스크랩하면 404를 반환한다`() {
        scrap(token, p2 + 999).andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("PROBLEM_NOT_FOUND") }
        }
        assertThat(scrapRepository.count()).isEqualTo(0)
    }

    @Test
    fun `스크랩을 해제하면 204이고 삭제된다`() {
        scrap(token, p1)

        unscrap(token, p1).andExpect { status { isNoContent() } }

        assertThat(scrapRepository.existsByUserIdAndProblemId(userId, p1)).isFalse()
    }

    @Test
    fun `스크랩하지 않은 문제를 해제해도 204이며 오류가 아니다`() {
        unscrap(token, p1).andExpect { status { isNoContent() } }
    }

    @Test
    fun `스크랩 목록은 본인 것만 최신순으로 준다`() {
        scrap(token, p1)
        clock.advance(Duration.ofSeconds(10)) // p2가 더 최신이 되도록 시간을 전진시킨다.
        scrap(token, p2)

        mockMvc.get("/api/scraps") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            // 최신순: 나중에 스크랩한 p2가 앞.
            jsonPath("$[0].problemId") { value(p2) }
            jsonPath("$[0].question") { value("큐 질문") }
            jsonPath("$[0].scrappedAt") { exists() }
            jsonPath("$[1].problemId") { value(p1) }
        }
    }

    @Test
    fun `다른 사용자의 스크랩은 목록에 보이지 않는다`() {
        scrap(token, p1)

        val otherToken = issueTokenForNewUser()
        mockMvc.get("/api/scraps") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `스크랩한 문제 상세는 개념과 모범답안과 해설을 준다`() {
        scrap(token, p1)

        mockMvc.get("/api/scraps/$p1") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.problemId") { value(p1) }
            jsonPath("$.question") { value("스택 질문") }
            jsonPath("$.concepts[0]") { value("스택") }
            jsonPath("$.acceptableAnswers[0]") { value("스택") }
            jsonPath("$.explanation") { value("스택 해설") }
            jsonPath("$.scrappedAt") { exists() }
        }
    }

    @Test
    fun `다른 사용자가 스크랩한 문제 상세는 404를 반환한다`() {
        // A가 p1을 스크랩해도, B는 자신이 스크랩하지 않았으므로 열람할 수 없다(사용자 격리).
        scrap(token, p1)

        val otherToken = issueTokenForNewUser()
        mockMvc.get("/api/scraps/$p1") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("SCRAP_NOT_FOUND") }
        }
    }

    @Test
    fun `스크랩하지 않은 문제 상세는 404를 반환한다`() {
        mockMvc.get("/api/scraps/$p1") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("SCRAP_NOT_FOUND") }
        }
    }

    @Test
    fun `토큰 없이 스크랩하면 401을 반환한다 (permitAll 회귀 방지)`() {
        mockMvc.post("/api/problems/$p1/scraps")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
        assertThat(scrapRepository.count()).isEqualTo(0)
    }

    @Test
    fun `토큰 없이 스크랩 목록을 조회하면 401을 반환한다`() {
        mockMvc.get("/api/scraps")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `계정을 삭제하면 그 사용자의 스크랩도 함께 삭제된다`() {
        scrap(token, p1)
        scrap(token, p2)
        assertThat(scrapRepository.count()).isEqualTo(2)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        assertThat(scrapRepository.count()).isEqualTo(0)
    }

    private fun scrap(bearer: String, problemId: Long) =
        mockMvc.post("/api/problems/$problemId/scraps") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun unscrap(bearer: String, problemId: Long) =
        mockMvc.delete("/api/problems/$problemId/scraps") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun seedProblem(conceptName: String, answer: String): Long {
        val concept = conceptRepository.save(Concept(conceptName))
        return problemRepository.save(
            Problem(
                questionText = "$conceptName 질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf(answer),
                difficulty = Difficulty.EASY,
                explanation = "$conceptName 해설",
            ),
        ).id
    }

    private fun issueTokenForNewUser(): String {
        val user = userRepository.save(User.createGuest())
        return jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    /** 스크랩 시각을 테스트에서 결정적으로 전진시키는 시계. 최신순 정렬을 흔들림 없이 검증하기 위함. */
    class MutableClock(private val base: Instant, private val zone: ZoneId) : Clock() {
        private var current: Instant = base

        fun reset() {
            current = base
        }

        fun advance(duration: Duration) {
            current = current.plus(duration)
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
            MutableClock(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul"))
    }
}
