package watson.bytecs.admin

import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Instant
import java.time.LocalDate

/**
 * 관리자 통계 페이지(`/admin/stats`)가 인증을 요구하고, 인증된 관리자에게 테스터 지표를 렌더링하는지 검증한다.
 * 집계 값의 정확성은 [watson.bytecs.session.infrastructure.SessionMetricsRepositoryTest]가 담당하므로,
 * 여기서는 접근 제어와 화면이 실제 집계 결과를 표시하는지에 집중한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminStatsControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        userRepository.deleteAll()
        userRepository.save(User.createAdmin(Email(ADMIN_EMAIL), passwordEncoder.encode(ADMIN_PASSWORD)))
    }

    @Test
    fun `비로그인 상태로 통계 페이지에 접근하면 로그인으로 리다이렉트된다`() {
        mockMvc.get("/admin/stats")
            .andExpect {
                status { is3xxRedirection() }
                redirectedUrlPattern("**/admin/login")
            }
    }

    @Test
    fun `관리자는 로그인 후 통계 페이지에서 집계된 지표를 확인한다`() {
        // given
        // 완료 후 추가 학습(지표 3)에 해당하는 사용자 한 명 — 완료 세션과 그 이후 시작된 세션.
        completedSession(userId = STUDIED_MORE_USER)
        startedSession(userId = STUDIED_MORE_USER)

        val adminSession = login()

        // when and then
        mockMvc.get("/admin/stats") {
            session = adminSession
        }.andExpect {
            status { isOk() }
            content { string(containsString("문제 풀이를 시작한 사용자")) }
            content { string(containsString("세션을 완료한 사용자")) }
            content { string(containsString("완료 후 더 푼 사용자")) }
        }
    }

    private fun login(): MockHttpSession {
        val loginResult = mockMvc.perform(formLogin("/admin/login").user(ADMIN_EMAIL).password(ADMIN_PASSWORD))
            .andReturn()
        return loginResult.request.session as MockHttpSession
    }

    private fun startedSession(userId: Long): Session =
        sessionRepository.save(
            Session.assign(userId, TODAY, listOf(PROBLEM_ID)).apply { markStarted(NOW) },
        )

    private fun completedSession(userId: Long): Session =
        sessionRepository.save(
            Session.assign(userId, TODAY, listOf(PROBLEM_ID)).apply {
                markStarted(NOW)
                recordAttempt(Judgement.CORRECT, AnswerText("정답"))
                markCompleted(NOW)
            },
        )

    private companion object {
        const val ADMIN_EMAIL = "admin@bytecs.dev"
        const val ADMIN_PASSWORD = "admin-password-1234"

        val TODAY: LocalDate = LocalDate.of(2026, 7, 14)
        val NOW: Instant = Instant.parse("2026-07-14T00:10:00Z")
        const val PROBLEM_ID = 100L
        const val STUDIED_MORE_USER = 1L
    }
}
