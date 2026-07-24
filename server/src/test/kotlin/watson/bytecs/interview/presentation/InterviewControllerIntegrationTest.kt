package watson.bytecs.interview.presentation

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import watson.bytecs.account.domain.Email
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
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.infrastructure.SessionRepository
import org.junit.jupiter.api.AfterEach
import java.time.LocalDate

/**
 * 면접 컨트롤러(C2)의 경로·보안 체인·JSON 직렬화를 MockMvc로 회귀 가드한다([review-todo] #1).
 * 서비스 유닛 테스트·H2 영속성 테스트가 도메인 로직을 덮으므로, 여기서는 HTTP 계층 계약(인증 요구·회원 게이트·
 * no-leak 필드·완료 흐름)만 실증한다. 채점기는 기본 [watson.bytecs.interview.infrastructure.FakeExplanationJudge]
 * (루브릭 포인트 문구 부분 매칭)를 그대로 쓴다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InterviewControllerIntegrationTest(
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

    private lateinit var memberToken: String

    private companion object {
        const val QUESTION = "프로세스와 스레드의 차이를 설명해보세요"
        const val MODEL_ANSWER = "프로세스는 자원 할당 단위, 스레드는 실행 단위입니다."
        const val RUBRIC_POINT = "실행 단위"
        const val CONCEPT_NAME = "프로세스와 스레드"
    }

    @BeforeEach
    fun setUp() {
        cleanUp()

        // 승급 후보 하나: 개념을 무도움으로 통과(레벨 1 ≥ 임계)해 두고, 그 개념에 승인된 면접 질문을 시드한다.
        val concept = conceptRepository.save(Concept(CONCEPT_NAME))
        val member = userRepository.save(User.createMember(Email("interview-it@example.com"), "hash"))
        memberToken = jwtTokenProvider.issue(member.id, UserRole.MEMBER)
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(member.id, concept.id, MasterySignal.UNAIDED, LocalDate.now(), 1L),
        )
        interviewPromptRepository.save(
            InterviewPrompt(
                concept = concept,
                question = QUESTION,
                modelAnswer = MODEL_ANSWER,
                rubricPoints = listOf(RUBRIC_POINT),
                approvalStatus = ApprovalStatus.APPROVED,
            ),
        )
    }

    @Test
    fun `회원 상태 조회는 후보 수와 잔여 쿼터를 준다`() {
        getStatus(memberToken).andExpect {
            status { isOk() }
            jsonPath("$.isGuest") { value(false) }
            jsonPath("$.candidateConceptCount") { value(1) }
            jsonPath("$.remainingQuota") { value(1) }
        }
    }

    @Test
    fun `게스트도 상태를 조회할 수 있고 잔여 쿼터는 0이다`() {
        getStatus(guestToken()).andExpect {
            status { isOk() }
            jsonPath("$.isGuest") { value(true) }
            jsonPath("$.remainingQuota") { value(0) }
        }
    }

    @Test
    fun `세션을 생성하면 현재 질문을 주되 모범 설명과 루브릭은 새지 않는다`() {
        val result = createSession(memberToken).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("IN_PROGRESS") }
            jsonPath("$.position") { value(0) }
            jsonPath("$.totalCount") { value(1) }
            jsonPath("$.currentQuestion") { value(QUESTION) }
            jsonPath("$.currentConceptName") { value(CONCEPT_NAME) }
            jsonPath("$.currentPromptId") { exists() }
            // no-leak: 제출 전에는 모범 설명·루브릭이 응답 어디에도 없어야 한다.
            jsonPath("$.modelAnswer") { doesNotExist() }
            jsonPath("$.rubricPoints") { doesNotExist() }
            jsonPath("$.points") { doesNotExist() }
        }.andReturn()

        assertThat(bodyOf(result)).doesNotContain(MODEL_ANSWER).doesNotContain(RUBRIC_POINT)
    }

    @Test
    fun `설명을 제출하면 채점 체크리스트와 모범 설명이 공개되고 세션이 완료된다`() {
        createSession(memberToken)

        // FakeExplanationJudge는 루브릭 포인트 문구를 부분 포함하면 충족으로 본다 → 전 포인트 충족(검증됨).
        submit(memberToken, "스레드는 실행 단위라서 문맥 전환이 가볍습니다").andExpect {
            status { isOk() }
            jsonPath("$.judged") { value(true) }
            jsonPath("$.points[0].text") { value(RUBRIC_POINT) }
            jsonPath("$.points[0].satisfied") { value(true) }
            jsonPath("$.modelAnswer") { value(MODEL_ANSWER) }
            jsonPath("$.conceptName") { value(CONCEPT_NAME) }
            // 유일한 문항이라 이 제출로 세션이 완료된다.
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.nextQuestion") { doesNotExist() }
            jsonPath("$.practicedConceptCount") { value(1) }
            jsonPath("$.streak.count") { value(greaterThanOrEqualTo(1)) }
        }
    }

    @Test
    fun `쿼터를 소진하면 세션 생성이 409를 반환한다`() {
        createSession(memberToken)
        submit(memberToken, "스레드는 실행 단위입니다") // 채점 성공 → 오늘 쿼터 소진.

        createSession(memberToken).andExpect {
            status { isConflict() }
            jsonPath("$.errorCode") { value("INTERVIEW_QUOTA_EXCEEDED") }
        }
    }

    @Test
    fun `게스트가 세션을 생성하면 403 회원 전용을 반환한다`() {
        createSession(guestToken()).andExpect {
            status { isForbidden() }
            jsonPath("$.errorCode") { value("INTERVIEW_MEMBER_ONLY") }
        }
    }

    @Test
    fun `승급 후보가 없는 회원이 세션을 생성하면 404를 반환한다`() {
        conceptMasteryRepository.deleteAll() // 승급 개념 제거 → 후보 없음.

        createSession(memberToken).andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("INTERVIEW_NO_CANDIDATE") }
        }
    }

    @Test
    fun `진행 중인 오늘 세션이 없을 때 조회하면 404를 반환한다`() {
        getSession(memberToken).andExpect {
            status { isNotFound() }
            jsonPath("$.errorCode") { value("INTERVIEW_SESSION_NOT_FOUND") }
        }
    }

    @Test
    fun `인증 없이 상태를 조회하면 401을 반환한다`() {
        mockMvc.get("/api/interview/status").andExpect {
            status { isUnauthorized() }
            jsonPath("$.errorCode") { value("UNAUTHORIZED") }
        }
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    /**
     * 공유 H2를 FK-안전 순서로 비운다. 다른 @SpringBootTest가 남긴 Problem이 Concept를 참조하므로,
     * 면접 콘텐츠·학습 세션·Problem을 먼저 지워야 Concept 삭제가 FK 위반 없이 성공한다(전체 suite 실행 격리).
     */
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

    private fun guestToken(): String {
        val guest = userRepository.save(User.createGuest())
        return jwtTokenProvider.issue(guest.id, UserRole.GUEST)
    }

    private fun getStatus(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/interview/status") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun createSession(bearer: String): ResultActionsDsl =
        mockMvc.post("/api/interview/sessions/today") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun getSession(bearer: String): ResultActionsDsl =
        mockMvc.get("/api/interview/sessions/today") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
        }

    private fun submit(bearer: String, explanation: String): ResultActionsDsl =
        mockMvc.post("/api/interview/sessions/today/answers") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $bearer")
            contentType = MediaType.APPLICATION_JSON
            content = """{"explanation":"$explanation"}"""
        }

    private fun bodyOf(result: MvcResult): String =
        result.response.contentAsByteArray.toString(Charsets.UTF_8)
}
