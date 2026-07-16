package watson.bytecs.problem.presentation

import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository

@SpringBootTest
@AutoConfigureMockMvc
class ProblemControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
) {

    private var problemId: Long = 0

    private companion object {
        const val CONCEPT_NAME = "해시 충돌"
        const val EXPLANATION = "체이닝, 개방 주소법 등으로 해소한다."
    }

    @BeforeEach
    fun setUp() {
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        val concept = conceptRepository.save(Concept(CONCEPT_NAME))
        val problem = problemRepository.save(
            Problem(
                questionText = "서로 다른 키가 동일한 해시 인덱스로 매핑되는 현상은?",
                concepts = listOf(concept),
                acceptableAnswers = setOf("해시 충돌", "충돌", "collision"),
                // 개념 이름을 묻는 문제라 근접 판정 대상이다. (유형이 없으면 근접이 꺼져 NEAR_MISS 자체가 나오지 않는다)
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.MEDIUM,
                explanation = EXPLANATION,
            ),
        )
        problemId = problem.id
    }

    @Test
    fun `다음 문제는 개념과 허용답을 노출하지 않는다`() {
        mockMvc.get("/api/problems/next")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(problemId) }
                jsonPath("$.question") { exists() }
                jsonPath("$.difficulty") { value("MEDIUM") }
                // 정답을 유추할 수 있는 필드는 응답에 존재하지 않아야 한다.
                jsonPath("$.concepts") { doesNotExist() }
                jsonPath("$.acceptableAnswers") { doesNotExist() }
                jsonPath("$.explanation") { doesNotExist() }
            }
    }

    @Test
    fun `정답을 제출하면 CORRECT와 함께 개념을 공개한다`() {
        mockMvc.post("/api/problems/$problemId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"collision"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.concepts[0]") { value(CONCEPT_NAME) }
            jsonPath("$.explanation") { value(EXPLANATION) }
        }
    }

    @Test
    fun `오답을 제출하면 MISMATCH이며 개념을 노출하지 않는다`() {
        mockMvc.post("/api/problems/$problemId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"자바"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("MISMATCH") }
            jsonPath("$.concepts") { value(nullValue()) }
            jsonPath("$.explanation") { value(nullValue()) }
        }
    }

    @Test
    fun `오탈자_수준의_답을_제출하면_NEAR_MISS이며_개념을_노출하지_않는다`() {
        mockMvc.post("/api/problems/$problemId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            // "collision"에서 i 하나가 빠진 오타.
            content = """{"answer":"collsion"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("NEAR_MISS") }
            jsonPath("$.concepts") { value(nullValue()) }
            jsonPath("$.explanation") { value(nullValue()) }
        }
    }

    @Test
    fun `존재하지_않는_문제에_제출하면_404를_반환한다`() {
        val unknownId = problemId + 9_999

        mockMvc.post("/api/problems/$unknownId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"collision"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("PROBLEM_NOT_FOUND") }
            jsonPath("$.message") { exists() }
        }
    }

    @Test
    fun `답을_비워_제출하면_4xx를_반환한다`() {
        mockMvc.post("/api/problems/$problemId/attempts") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"   "}"""
        }.andExpect {
            status { is4xxClientError() }
        }
    }
}
