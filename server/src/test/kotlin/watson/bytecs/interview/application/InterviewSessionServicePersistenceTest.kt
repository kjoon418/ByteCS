package watson.bytecs.interview.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.interview.infrastructure.InterviewReadinessRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import java.time.LocalDate

/**
 * 면접 세션 제출을 실제 저장소(H2) 위에서 끝까지 실행해, 목(mock) 기반 유닛 테스트가 가릴 수 있는 제약 조건
 * 위반(예: NOT NULL 컬럼에 값이 채워지기 전에 save되는 순서 버그)을 잡는다.
 * 실서버 스모크(로컬 bootRun)에서 정확히 이 클래스의 버그로 409가 났던 것을 계기로 추가했다 — 회귀 가드.
 */
@SpringBootTest
class InterviewSessionServicePersistenceTest(
    @Autowired private val interviewSessionService: InterviewSessionService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val interviewPromptRepository: InterviewPromptRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
    @Autowired private val interviewSessionRepository: InterviewSessionRepository,
    @Autowired private val interviewReadinessRepository: InterviewReadinessRepository,
) {

    private lateinit var user: User
    private lateinit var concept: Concept

    @BeforeEach
    fun setUp() {
        cleanUp()
        user = userRepository.save(User.createMember(Email("interview-persistence-test@example.com"), "hash"))
        concept = conceptRepository.save(Concept("영속성 테스트 개념"))
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(user.id, concept.id, MasterySignal.UNAIDED, LocalDate.now(), 1L),
        )
        interviewPromptRepository.save(
            InterviewPrompt(
                concept = concept,
                question = "영속성 테스트 질문",
                modelAnswer = "핵심 포인트를 담은 모범 설명입니다.",
                rubricPoints = listOf("핵심 포인트"),
                approvalStatus = ApprovalStatus.APPROVED,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        cleanUp()
    }

    private fun cleanUp() {
        interviewReadinessRepository.deleteAll()
        interviewSessionRepository.deleteAll()
        interviewPromptRepository.deleteAll()
        conceptMasteryRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `면접 세션 생성과 제출이 실제 DB에 끝까지 커밋된다(준비도 upsert 포함)`() {
        interviewSessionService.createTodaySession(user.id)

        val response = interviewSessionService.submitAnswer(user.id, "핵심 포인트를 짚은 제 설명입니다.")

        assertThat(response.judged).isTrue()
        assertThat(response.status).isEqualTo("COMPLETED")
        val readiness = interviewReadinessRepository.findByUserIdAndConceptId(user.id, concept.id)
        assertThat(readiness).isNotNull
        assertThat(readiness!!.satisfiedCount).isEqualTo(1)
    }
}
