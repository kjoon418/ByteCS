package watson.bytecs.account.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.delete
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
) {

    private companion object {
        const val EMAIL = "member@bytecs.dev"
        const val PASSWORD = "password1234"
    }

    @BeforeEach
    fun setUp() {
        userRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        // 보안 permitAll 회귀 검증이 실제 200을 확인하도록 문제 하나를 시드한다.
        val concept = conceptRepository.save(Concept("해시 충돌"))
        problemRepository.save(
            Problem(
                questionText = "서로 다른 키가 동일한 해시 인덱스로 매핑되는 현상은?",
                concepts = listOf(concept),
                acceptableAnswers = setOf("해시 충돌", "collision"),
                difficulty = Difficulty.MEDIUM,
                explanation = "체이닝, 개방 주소법 등으로 해소한다.",
            ),
        )
    }

    @Test
    fun `게스트를 발급하면 201과 토큰_식별자_역할을 반환한다`() {
        mockMvc.post("/api/guests")
            .andExpect {
                status { isCreated() }
                jsonPath("$.token") { exists() }
                jsonPath("$.userId") { exists() }
                jsonPath("$.role") { value("GUEST") }
            }
    }

    @Test
    fun `토큰 없이 내 정보를 조회하면 401을 반환한다`() {
        mockMvc.get("/api/users/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `새 이메일로 가입하면 토큰을 받고 내 정보는 MEMBER와 이메일을 노출한다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.get("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("MEMBER") }
            jsonPath("$.email") { value(EMAIL) }
        }
    }

    @Test
    fun `게스트 토큰으로 가입하면 같은 식별자로 승격된다`() {
        val guestResult = mockMvc.post("/api/guests")
            .andReturn()
        val guestUserId = readField(guestResult, "userId").asLong()
        val guestToken = readField(guestResult, "token").asText()

        val memberToken = register(EMAIL, PASSWORD, bearerToken = guestToken)

        val meResult = mockMvc.get("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
        }.andReturn()
        val memberUserId = readField(meResult, "userId").asLong()

        // 승격은 새 계정을 만들지 않고 게스트의 id를 유지해야 한다(학습 상태 승계).
        assertThat(memberUserId).isEqualTo(guestUserId)
    }

    @Test
    fun `이미 사용 중인 이메일로 가입하면 409를 반환한다`() {
        register(EMAIL, PASSWORD)

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$EMAIL","password":"$PASSWORD"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("EMAIL_DUPLICATED") }
        }
    }

    @Test
    fun `올바른 자격 증명으로 로그인하면 토큰을 받는다`() {
        register(EMAIL, PASSWORD)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$EMAIL","password":"$PASSWORD"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.token") { exists() }
        }
    }

    @Test
    fun `비밀번호가 틀리면 401 INVALID_CREDENTIALS를 반환한다`() {
        register(EMAIL, PASSWORD)

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$EMAIL","password":"wrong-password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `알 수 없는 이메일로 로그인해도 동일한 401 INVALID_CREDENTIALS를 반환한다`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"unknown@bytecs.dev","password":"$PASSWORD"}"""
        }.andExpect {
            // 계정 열거 방지: 이메일 없음도 비밀번호 불일치와 같은 코드로 응답한다.
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `비밀번호는 해시로 저장되며 원문과 다르고 BCrypt로 매칭된다`() {
        register(EMAIL, PASSWORD)

        val stored = userRepository.findByEmail(EMAIL)!!
        assertThat(stored.passwordHash).isNotEqualTo(PASSWORD)
        assertThat(passwordEncoder.matches(PASSWORD, stored.passwordHash)).isTrue()
    }

    @Test
    fun `설정을 유효 범위로 변경하면 200과 변경값을 반환한다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"dailySessionSize":20}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dailySessionSize") { value(20) }
        }
    }

    @Test
    fun `설정을 허용 범위 밖으로 변경하면 400을 반환한다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"dailySessionSize":999}"""
        }.andExpect {
            status { is4xxClientError() }
        }
    }

    @Test
    fun `탈퇴하면 204이고 이후 내 정보 조회는 404를 반환한다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

        mockMvc.get("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `변조된 Bearer 토큰으로 내 정보를 조회하면 401을 반환한다`() {
        // 서명 검증에 실패하는 토큰은 인증되지 않은 것으로 취급되어 진입점에서 401로 걸러져야 한다(필터→진입점 배선 e2e).
        mockMvc.get("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer xxx.yyy.zzz")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `이미 회원인 토큰으로 다시 가입하면 409 INVALID_USER_STATE를 반환한다`() {
        val memberToken = register(EMAIL, PASSWORD)

        // 다른 이메일로 시도해야 중복 검사를 지나 승격 가드(promoteToMember)에 도달한다.
        mockMvc.post("/api/auth/register") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $memberToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"another@bytecs.dev","password":"$PASSWORD"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("INVALID_USER_STATE") }
        }
    }

    @Test
    fun `탈퇴를 두 번 호출하면 204 뒤 404이며 500이 아니다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

        // 이미 삭제된 사용자를 다시 삭제하면 500이 아니라 404로 응답해야 한다.
        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `문제 조회는 토큰 없이도 200을 반환한다 (permitAll 회귀)`() {
        mockMvc.get("/api/problems/next")
            .andExpect {
                status { isOk() }
            }
    }

    /** 가입해 토큰을 반환한다. bearerToken을 주면 그 인증 하에 가입(게스트 승격)한다. */
    private fun register(email: String, password: String, bearerToken: String? = null): String {
        val result = mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
            bearerToken?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }.andReturn()

        return readField(result, "token").asText()
    }

    private fun readField(result: MvcResult, field: String) =
        objectMapper.readTree(result.response.contentAsByteArray).get(field)
}
