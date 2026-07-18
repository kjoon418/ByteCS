package watson.bytecs.admin

import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider

/**
 * 관리자 보안 체인(`/admin` 하위 경로)의 접근 제어와, 기존 무상태 API 체인이 영향을 받지 않음을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        userRepository.save(User.createAdmin(Email(ADMIN_EMAIL), passwordEncoder.encode(ADMIN_PASSWORD)))
    }

    @Test
    fun `비로그인 상태로 관리자 페이지에 접근하면 로그인으로 리다이렉트된다`() {
        mockMvc.get("/admin")
            .andExpect {
                status { is3xxRedirection() }
                redirectedUrlPattern("**/admin/login")
            }
    }

    @Test
    fun `로그인 페이지는 비로그인 상태로 접근할 수 있고 CSRF 토큰 필드를 렌더링한다`() {
        // th:action 폼에 Spring Security의 CSRF 히든 필드가 자동 주입되어야 실제 브라우저 로그인이 403이 되지 않는다.
        mockMvc.get("/admin/login")
            .andExpect {
                status { isOk() }
                content { string(containsString("name=\"_csrf\"")) }
            }
    }

    @Test
    fun `게스트 토큰의 Bearer 헤더로는 관리자 페이지에 접근할 수 없다`() {
        // 관리자 체인은 세션 기반 폼 로그인만 인정한다 — JWT는 이 체인에서 인증 수단이 아니다.
        val guest = userRepository.save(User.createGuest())
        val guestToken = jwtTokenProvider.issue(guest.id, UserRole.GUEST)

        mockMvc.get("/admin") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrlPattern("**/admin/login")
        }
    }

    @Test
    fun `관리자는 폼 로그인 후 관리자 홈에 접근한다`() {
        // when
        val loginResult = mockMvc.perform(
            formLogin("/admin/login").user(ADMIN_EMAIL).password(ADMIN_PASSWORD),
        )
            .andExpect(redirectedUrl("/admin"))
            .andExpect(authenticated().withRoles("ADMIN"))
            .andReturn()

        // then
        val adminSession = loginResult.request.session as MockHttpSession
        mockMvc.get("/admin") {
            session = adminSession
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `일반 회원 자격으로는 관리자 로그인이 거부된다`() {
        // given
        userRepository.save(User.createMember(Email(MEMBER_EMAIL), passwordEncoder.encode(MEMBER_PASSWORD)))

        // when and then
        mockMvc.perform(formLogin("/admin/login").user(MEMBER_EMAIL).password(MEMBER_PASSWORD))
            .andExpect(redirectedUrl("/admin/login?error"))
    }

    @Test
    fun `로그아웃하면 로그인 페이지로 돌아가고 세션이 무효화된다`() {
        // given
        val loginResult = mockMvc.perform(formLogin("/admin/login").user(ADMIN_EMAIL).password(ADMIN_PASSWORD))
            .andReturn()
        val adminSession = loginResult.request.session as MockHttpSession

        // when
        mockMvc.post("/admin/logout") {
            session = adminSession
            with(csrf())
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/admin/login?logout")
        }

        // then
        mockMvc.get("/admin") {
            session = adminSession
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrlPattern("**/admin/login")
        }
    }

    @Test
    fun `기존 API 체인은 관리자 체인의 영향을 받지 않는다`() {
        // 게스트 발급은 여전히 열려 있고(무상태·CSRF 없음), 보호 API는 리다이렉트가 아니라 401 JSON으로 응답한다.
        mockMvc.post("/api/guests")
            .andExpect {
                status { isCreated() }
            }

        mockMvc.get("/api/users/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    private companion object {
        const val ADMIN_EMAIL = "admin@bytecs.dev"
        const val ADMIN_PASSWORD = "admin-password-1234"
        const val MEMBER_EMAIL = "member@bytecs.dev"
        const val MEMBER_PASSWORD = "member-password-1234"
    }
}
