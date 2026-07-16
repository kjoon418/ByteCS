package watson.bytecs.report.presentation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
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
import watson.bytecs.report.domain.ReportCategory
import watson.bytecs.report.infrastructure.ContentReportRepository

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val contentReportRepository: ContentReportRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {

    private lateinit var token: String
    private var problemId: Long = 0
    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        contentReportRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        val concept = conceptRepository.save(Concept("해시 충돌"))
        problemId = problemRepository.save(
            Problem(
                questionText = "서로 다른 키가 동일한 해시 인덱스로 매핑되는 현상은?",
                concepts = listOf(concept),
                acceptableAnswers = setOf("해시 충돌"),
                difficulty = Difficulty.MEDIUM,
            ),
        ).id

        val user = userRepository.save(User.createGuest())
        userId = user.id
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `유형과 상세 내용을 함께 신고하면 201이고 둘 다 저장된다`() {
        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"QUESTION_ERROR","message":"설명이 틀렸어요."}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { exists() }
            jsonPath("$.problemId") { value(problemId) }
            jsonPath("$.category") { value("QUESTION_ERROR") }
            jsonPath("$.createdAt") { exists() }
        }

        val reports = contentReportRepository.findAll()
        assertThat(reports).hasSize(1)
        assertThat(reports[0].userId).isEqualTo(userId)
        assertThat(reports[0].problemId).isEqualTo(problemId)
        assertThat(reports[0].category).isEqualTo(ReportCategory.QUESTION_ERROR)
        assertThat(reports[0].message).isEqualTo("설명이 틀렸어요.")
    }

    @Test
    fun `상세 내용 없이 유형만 신고해도 201이고 message는 null로 저장된다`() {
        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"WRONG_ANSWER"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.category") { value("WRONG_ANSWER") }
        }

        val reports = contentReportRepository.findAll()
        assertThat(reports).hasSize(1)
        assertThat(reports[0].category).isEqualTo(ReportCategory.WRONG_ANSWER)
        assertThat(reports[0].message).isNull()
    }

    @Test
    fun `빈 상세 내용은 null로 정규화되어 저장된다`() {
        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"OTHER","message":"   "}"""
        }.andExpect {
            status { isCreated() }
        }

        assertThat(contentReportRepository.findAll()[0].message).isNull()
    }

    @Test
    fun `유형이 없으면 400을 반환하고 저장되지 않는다`() {
        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"message":"유형을 빼먹었어요."}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("INVALID_REQUEST") }
        }

        assertThat(contentReportRepository.count()).isEqualTo(0)
    }

    @Test
    fun `지원하지 않는 유형은 400을 반환하고 저장되지 않는다`() {
        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"SOMETHING_ELSE"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("INVALID_INPUT") }
        }

        assertThat(contentReportRepository.count()).isEqualTo(0)
    }

    @Test
    fun `없는 문제를 신고하면 404를 반환한다`() {
        mockMvc.post("/api/problems/${problemId + 999}/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"WRONG_ANSWER"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("PROBLEM_NOT_FOUND") }
        }

        assertThat(contentReportRepository.count()).isEqualTo(0)
    }

    @Test
    fun `토큰 없이 신고하면 401을 반환한다 (permitAll 회귀 방지)`() {
        // /api/problems 하위는 permitAll이지만, 신고 경로만은 인증을 강제해야 한다.
        mockMvc.post("/api/problems/$problemId/reports") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"WRONG_ANSWER"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("UNAUTHORIZED") }
        }

        assertThat(contentReportRepository.count()).isEqualTo(0)
    }

    // 계정 삭제 후에도 스테이트리스 JWT는 유효하다. 삭제된 사용자의 토큰으로 신고를 접수하면
    // 201(고아 행 생성)이 아니라 404 UserNotFound여야 한다(2026-07-16 오너 결정 8).

    @Test
    fun `계정을 삭제한 뒤 그 토큰으로 신고하면 404이고 고아 행이 생기지 않는다`() {
        deleteAccount(token)

        mockMvc.post("/api/problems/$problemId/reports") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"category":"WRONG_ANSWER"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
        }

        // 사용자 존재 검사가 저장보다 먼저 실행돼, 삭제된 userId로 참조 무결성 없는 신고 테이블에 고아 행이 남지 않는지 확인한다.
        assertThat(contentReportRepository.count()).isEqualTo(0)
    }

    private fun deleteAccount(bearer: String) {
        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }.andExpect { status { isNoContent() } }
    }
}
