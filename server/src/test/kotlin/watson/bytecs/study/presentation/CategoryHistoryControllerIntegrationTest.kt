package watson.bytecs.study.presentation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 카테고리별 학습 이력 API의 통합 계약을 검증한다(세션 실제 플로우로 문제를 푼 뒤 그룹핑을 확인한다).
 * 세션 배정 순서는 무작위라 어떤 문제가 먼저 나오든 답을 맞게 찾아 제출한다(answersById 관례).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryHistoryControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {

    private lateinit var token: String
    private var p1: Long = 0 // DATA_STRUCTURE
    private var p2: Long = 0 // NETWORK
    private val answersById = mutableMapOf<Long, String>()

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
        answersById.clear()

        p1 = seedProblem("개념1", "정답1", ProblemCategory.DATA_STRUCTURE)
        p2 = seedProblem("개념2", "정답2", ProblemCategory.NETWORK)

        val user = userRepository.save(User.createGuest())
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `아무것도 풀지 않았으면 8개 카테고리 전체가 빈 목록으로 온다`() {
        getCategories(token).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(8) }
            // 순서는 ProblemCategory 선언 순서를 따른다.
            jsonPath("$[0].category") { value("DATA_STRUCTURE") }
            jsonPath("$[7].category") { value("SECURITY") }
            jsonPath("$[0].items") { isEmpty() }
            jsonPath("$[7].items") { isEmpty() }
        }
    }

    @Test
    fun `세션에서 푼 문제들이 각 대표 분류로 그룹핑되고 그때 제출한 답이 실린다`() {
        // 한 세션에 배정된 두 문제를 차례로 통과시킨다(배정 순서는 무작위이므로 현재 문제의 답을 찾아 제출한다).
        val firstSolvedId = solveCurrentSessionItem()
        val secondSolvedId = solveCurrentSessionItem()

        assertThat(setOf(firstSolvedId, secondSolvedId)).containsExactlyInAnyOrder(p1, p2)

        val result = getCategories(token).andExpect { status { isOk() } }.andReturn()
        val tree = objectMapper.readTree(result.response.contentAsByteArray)
        assertThat(tree.size()).isEqualTo(8)
        val groupsByCategory = tree.associateBy { it.get("category").asText() }

        // 세션에서 통과한 두 문제는 각자의 대표 분류로 그룹핑되고, 그때 제출한 답이 그대로 실린다.
        listOf(firstSolvedId, secondSolvedId).forEach { solvedId ->
            val group = groupsByCategory.getValue(categoryOf(solvedId).name)
            assertThat(group.get("items").size()).isEqualTo(1)
            assertThat(group.get("items")[0].get("problemId").asLong()).isEqualTo(solvedId)
            assertThat(group.get("items")[0].get("submittedAnswer").asText()).isEqualTo(answersById.getValue(solvedId))
            assertThat(group.get("items")[0].get("result").asText()).isEqualTo("CORRECT")
            assertThat(group.get("items")[0].get("representativeAnswer").asText()).isEqualTo(answersById.getValue(solvedId))
        }

        // 아무도 풀지 않은 나머지 6개 카테고리는 빈 목록이다.
        val untouchedCategories = ProblemCategory.entries.map { it.name } -
            setOf(categoryOf(firstSolvedId).name, categoryOf(secondSolvedId).name)
        untouchedCategories.forEach { category ->
            assertThat(groupsByCategory.getValue(category).get("items")).isEmpty()
        }
    }

    @Test
    fun `인증 없이 조회하면 401을 반환한다`() {
        mockMvc.get("/api/learning-history/categories")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `계정을 삭제한 뒤 그 토큰으로 조회하면 404를 반환한다`() {
        mockMvc.delete("/api/users/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }.andExpect { status { isNoContent() } }

        getCategories(token).andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `사용자마다 학습 이력이 격리된다`() {
        solveCurrentSessionItem()

        val otherUser = userRepository.save(User.createGuest())
        val otherToken = jwtTokenProvider.issue(otherUser.id, UserRole.GUEST)

        getCategories(otherToken).andExpect {
            status { isOk() }
            jsonPath("$[0].items") { isEmpty() }
            jsonPath("$[7].items") { isEmpty() }
        }
    }

    // --- helpers ---

    /** 오늘 세션의 현재 문제를 정답으로 통과시키고, 그 문제 id를 돌려준다. */
    private fun solveCurrentSessionItem(): Long {
        val tree = objectMapper.readTree(getToday(token).andReturn().response.contentAsByteArray)
        val id = tree.get("currentProblem").get("id").asLong()
        submitSession(token, answersById.getValue(id)).andExpect { status { isOk() } }
        return id
    }

    private fun categoryOf(problemId: Long): ProblemCategory =
        if (problemId == p1) ProblemCategory.DATA_STRUCTURE else ProblemCategory.NETWORK

    private fun getCategories(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/learning-history/categories") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun getToday(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/sessions/today") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun submitSession(bearer: String, answer: String): ResultActionsDsl =
        mockMvc.post("/api/sessions/today/attempts") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"$answer"}"""
        }

    private fun seedProblem(conceptName: String, answer: String, category: ProblemCategory): Long {
        val concept = conceptRepository.save(Concept(conceptName, category = category))
        val problem = problemRepository.save(
            Problem(
                // 통합 테스트 시드는 서빙 중인 문제를 표현하므로 승인 상태로 넣는다(서빙 게이트).
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "$conceptName 질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf(answer),
                representativeAnswer = answer,
            ),
        )
        answersById[problem.id] = answer
        return problem.id
    }
}
