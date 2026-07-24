package watson.bytecs.session.presentation

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.account.security.JwtTokenProvider
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.interview.infrastructure.InterviewReadinessRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate

/**
 * DI9 정답 순간 면접 승급 알림의 **읽기-쓰기 순서(플러시) 계약**을 실 JPA로 못박는다.
 *
 * 유닛 테스트는 [watson.bytecs.interview.application.InterviewUnlockCalculator]와 저장소를 목으로 덮어 이 계약을 검증하지 못한다:
 * `SessionService.submitAnswer`가 **쓰기 트랜잭션**에서 `recordSolve`로 숙련도 레벨을 올린 **뒤** 후보 재조회(JPQL)가 그 갱신을
 * 보는 것은 Hibernate의 AUTO 플러시에 달려 있다. 훗날 누군가 submitAnswer를 readOnly로 바꾸면(또는 플러시 모드가 MANUAL로 바뀌면)
 * 승급이 조용히 사라지는데, 목 기반 테스트로는 그 회귀를 못 잡는다 — 그래서 HTTP→서비스→실 DB 전 구간을 한 번 통과시킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SessionInterviewPromotionIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
    @Autowired private val interviewPromptRepository: InterviewPromptRepository,
    @Autowired private val interviewSessionRepository: InterviewSessionRepository,
    @Autowired private val interviewReadinessRepository: InterviewReadinessRepository,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {

    private lateinit var token: String
    private var userId: Long = 0
    private var conceptId: Long = 0

    private companion object {
        const val CONCEPT_NAME = "프로세스와 스레드"
        const val ANSWER = "정답"
    }

    @BeforeEach
    fun setUp() {
        cleanUp()

        // 승급 후보가 될 수 있는 최소 시드: 개념 하나 + 그 개념을 정답 통과할 수 있는 승인 문제 + 그 개념의 승인된 면접 질문.
        val concept = conceptRepository.save(Concept(CONCEPT_NAME))
        conceptId = concept.id
        problemRepository.save(
            Problem(
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf(ANSWER),
                representativeAnswer = ANSWER,
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
                explanation = "해설",
            ),
        )
        interviewPromptRepository.save(
            InterviewPrompt(
                concept = concept,
                question = "면접 질문",
                modelAnswer = "모범 설명",
                rubricPoints = listOf("핵심"),
                approvalStatus = ApprovalStatus.APPROVED,
            ),
        )

        val user = userRepository.save(User.createGuest())
        userId = user.id
        token = jwtTokenProvider.issue(user.id, UserRole.GUEST)
    }

    @Test
    fun `무도움 정답으로 개념이 처음 후보가 되면 정답 응답에 승급 개념명이 실린다`() {
        getToday(token) // 오늘 세션 생성(승인 문제 하나 배정)

        // 힌트·공개 없이 곧장 맞힘 → 무도움 정답 → 숙련도 0→1(첫 정착선). 같은 트랜잭션에서 후보 재조회가 이 갱신을 봐야 한다.
        submit(token, ANSWER).andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.newlyEligibleConcepts.length()") { value(1) }
            jsonPath("$.newlyEligibleConcepts[0]") { value(CONCEPT_NAME) }
        }
    }

    @Test
    fun `이미 후보였던 개념을 다시 맞히면 승급 개념명이 실리지 않는다`() {
        // 정답 통과 전에 이미 후보(레벨≥임계)로 만들어 둔다 → 이번 정답은 '새로 열림'이 아니다.
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(userId, conceptId, MasterySignal.UNAIDED, LocalDate.now(), 1L),
        )
        getToday(token)

        submit(token, ANSWER).andExpect {
            status { isOk() }
            jsonPath("$.result") { value("CORRECT") }
            jsonPath("$.newlyEligibleConcepts.length()") { value(0) }
        }
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    /** 공유 H2를 FK-안전 순서로 비운다(면접 콘텐츠·학습 세션·Problem을 Concept보다 먼저 — InterviewControllerIntegrationTest와 동일 규율). */
    private fun cleanUp() {
        interviewReadinessRepository.deleteAll()
        interviewSessionRepository.deleteAll()
        interviewPromptRepository.deleteAll()
        sessionRepository.deleteAll()
        conceptMasteryRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
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
}
