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
import watson.bytecs.report.domain.ContentReport
import watson.bytecs.report.domain.ReportCategory
import watson.bytecs.report.infrastructure.ContentReportRepository
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val contentReportRepository: ContentReportRepository,
) {

    private companion object {
        const val EMAIL = "member@bytecs.dev"
        const val PASSWORD = "password1234"
    }

    @BeforeEach
    fun setUp() {
        contentReportRepository.deleteAll()
        userRepository.deleteAll()
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
    fun `잘못된 이메일 형식으로 가입하면 400과 INVALID_INPUT을 반환한다`() {
        // QA #5: 클라가 오안내(연결 실패)하던 버그의 원인이 된 현재 서버 동작을 사양으로 고정한다.
        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"aaa@aaa","password":"$PASSWORD"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("INVALID_INPUT") }
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
    fun `선호 난이도를 설정하면 200과 그 값을 반환하고 제안 응답도 완료로 기록된다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"preferredDifficulty":"EASY"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.preferredDifficulty") { value("EASY") }
        }

        // 직접 설정한 사용자에게는 완료 화면에서 다시 제안하지 않는다(promptDone=true).
        val stored = userRepository.findByEmail(EMAIL)!!
        assertThat(stored.difficultyPromptDone).isTrue()
        assertThat(stored.needsDifficultyPrompt()).isFalse()
    }

    @Test
    fun `선호 난이도를 알 수 없는 값으로 보내면 400을 반환한다`() {
        val token = register(EMAIL, PASSWORD)

        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"preferredDifficulty":"IMPOSSIBLE"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `제안 응답만 보내면 선호는 미설정으로 남고 다시 제안하지 않는다`() {
        val token = register(EMAIL, PASSWORD)

        // 완료 화면에서 '지금은 괜찮아요'(거절)를 누른 경우 — difficultyPromptDone만 true로 보낸다.
        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"difficultyPromptDone":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.preferredDifficulty") { value(null) }
        }

        val stored = userRepository.findByEmail(EMAIL)!!
        assertThat(stored.settings.preferredDifficulty).isNull()
        assertThat(stored.difficultyPromptDone).isTrue()
    }

    @Test
    fun `세션 분량만 바꾸면 이미 설정한 선호 난이도는 보존된다`() {
        val token = register(EMAIL, PASSWORD)
        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"preferredDifficulty":"HARD"}"""
        }.andExpect { status { isOk() } }

        // dailySessionSize만 담아 부분 갱신해도 선호 난이도는 초기화되지 않아야 한다.
        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"dailySessionSize":15}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.dailySessionSize") { value(15) }
            jsonPath("$.preferredDifficulty") { value("HARD") }
        }
    }

    @Test
    fun `게스트도 선호 난이도를 설정할 수 있다`() {
        val guestResult = mockMvc.post("/api/guests").andReturn()
        val guestToken = readField(guestResult, "token").asText()

        mockMvc.patch("/api/users/me/settings") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $guestToken")
            contentType = MediaType.APPLICATION_JSON
            content = """{"preferredDifficulty":"MEDIUM"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.role") { value("GUEST") }
            jsonPath("$.preferredDifficulty") { value("MEDIUM") }
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
    fun `탈퇴해도 그 사용자의 신고는 지워지지 않고 userId만 null로 익명화된다`() {
        // 신고는 학습 상태가 아니라 콘텐츠 품질 운영 데이터라(D10), 계정 삭제와 함께 지우지 않고 보존한다.
        val token = register(EMAIL, PASSWORD)
        val userId = requireNotNull(userRepository.findByEmail(EMAIL)).id
        val report = contentReportRepository.save(
            ContentReport(
                userId = userId,
                problemId = 1L,
                category = ReportCategory.WRONG_ANSWER,
                message = null,
                createdAt = Instant.now(),
            ),
        )

        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect {
            status { isNoContent() }
        }

        val persisted = contentReportRepository.findById(report.id).orElseThrow()
        assertThat(persisted.userId).isNull()
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
