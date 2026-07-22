package watson.bytecs.session.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.account.domain.User
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate

/**
 * 연결 문제 잠금 해제 계산([IntegrationUnlockCalculator], 계획 §3.2 · D2)을 실제 저장소 위에서 검증한다.
 * 개념·문제 식별자가 실제로 부여돼야(생성 전략) 게이트 로직이 성립하므로 SpringBootTest로 상태를 심는다.
 * '새로 열림' = 이 세션에서 처음 만난 개념이 마지막 조각이 되어 전 구성 개념이 학습된 지정 연결 문제.
 */
@SpringBootTest
class IntegrationUnlockCalculatorTest(
    @Autowired private val calculator: IntegrationUnlockCalculator,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
) {

    private var userId: Long = 0
    private val today: LocalDate = LocalDate.of(2026, 7, 22)

    @BeforeEach
    fun setUp() {
        conceptMasteryRepository.deleteAll()
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()
        userId = userRepository.save(User.createGuest()).id
    }

    @Test
    fun `이 세션에서 마지막 조각이 채워진 지정 연결 문제를 새로 열린 것으로 돌려준다`() {
        val a = conceptRepository.save(Concept("스택"))
        val b = conceptRepository.save(Concept("큐"))
        val pa = singleConceptProblem(a)
        val pb = singleConceptProblem(b)
        integrationProblem(listOf(a, b))
        // 이 세션에서 a·b를 처음 만나 둘 다 학습 → 연결 문제의 마지막 조각이 채워진다.
        val session = completedSession(listOf(pa.id, pb.id))
        learnConcept(a.id, pa.id)
        learnConcept(b.id, pb.id)

        val unlocked = calculator.calculate(userId, session)

        assertThat(unlocked).hasSize(1)
        assertThat(unlocked.single().concepts).containsExactly("스택", "큐")
    }

    @Test
    fun `이미 열려 있던 연결 문제는 다른 세션 완료에서 다시 등장하지 않는다`() {
        val a = conceptRepository.save(Concept("스택"))
        val b = conceptRepository.save(Concept("큐"))
        val c = conceptRepository.save(Concept("힙"))
        val pa = singleConceptProblem(a)
        val pb = singleConceptProblem(b)
        val pc = singleConceptProblem(c)
        integrationProblem(listOf(a, b))
        // 세션 1에서 a·b를 배워 연결 문제가 이미 열렸다.
        completedSession(listOf(pa.id, pb.id))
        learnConcept(a.id, pa.id)
        learnConcept(b.id, pb.id)
        // 세션 2에서는 c만 새로 배운다 — 이미 열린 a·b 연결 문제는 다시 축하하지 않는다.
        val session2 = completedSession(listOf(pc.id))
        learnConcept(c.id, pc.id)

        val unlocked = calculator.calculate(userId, session2)

        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `다른 세션에서 이미 푼 문제가 이 세션에 복습으로 재출제돼도 새 개념으로 세지 않는다`() {
        val a = conceptRepository.save(Concept("스택"))
        val b = conceptRepository.save(Concept("큐"))
        val pa = singleConceptProblem(a)
        val pb = singleConceptProblem(b)
        integrationProblem(listOf(a, b))
        // 세션 1에서 a·b를 모두 배워 연결 문제가 이미 열렸다.
        completedSession(listOf(pa.id, pb.id))
        learnConcept(a.id, pa.id)
        learnConcept(b.id, pb.id)
        // 세션 2는 세션 1에서 이미 푼 pa를 복습으로 재출제 — a는 이 세션에서 '처음 만난' 개념이 아니다.
        val session2 = completedSession(listOf(pa.id))

        val unlocked = calculator.calculate(userId, session2)

        // 재출제된 a를 새 개념으로 오인하면 연결 문제가 거짓으로 다시 열린다 — 그래선 안 된다.
        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `지정되지 않은 다개념 문제는 잠금 해제 대상이 아니다`() {
        val a = conceptRepository.save(Concept("스택"))
        val b = conceptRepository.save(Concept("큐"))
        val pa = singleConceptProblem(a)
        val pb = singleConceptProblem(b)
        // 개념 2개짜리 문제지만 integration=false — 게이트·잠금 해제 밖이다.
        multiConceptProblem(listOf(a, b))
        val session = completedSession(listOf(pa.id, pb.id))
        learnConcept(a.id, pa.id)
        learnConcept(b.id, pb.id)

        val unlocked = calculator.calculate(userId, session)

        assertThat(unlocked).isEmpty()
    }

    @Test
    fun `구성 개념 중 하나라도 아직 학습하지 않았으면 열리지 않는다`() {
        val a = conceptRepository.save(Concept("스택"))
        val b = conceptRepository.save(Concept("큐"))
        val pa = singleConceptProblem(a)
        singleConceptProblem(b)
        integrationProblem(listOf(a, b))
        // 이 세션에서 a만 배웠다 — b는 아직 미학습이라 연결 문제는 열리지 않는다.
        val session = completedSession(listOf(pa.id))
        learnConcept(a.id, pa.id)

        val unlocked = calculator.calculate(userId, session)

        assertThat(unlocked).isEmpty()
    }

    private fun completedSession(problemIds: List<Long>): Session {
        val session = Session.assign(userId, today, problemIds)
        repeat(problemIds.size) { session.recordAttempt(Judgement.CORRECT, AnswerText("정답")) }
        return sessionRepository.save(session)
    }

    private fun learnConcept(conceptId: Long, problemId: Long) {
        conceptMasteryRepository.save(
            ConceptMastery.firstSolve(userId, conceptId, MasterySignal.UNAIDED, today, problemId),
        )
    }

    private fun singleConceptProblem(concept: Concept): Problem = saveProblem(listOf(concept), integration = false)

    private fun multiConceptProblem(concepts: List<Concept>): Problem = saveProblem(concepts, integration = false)

    private fun integrationProblem(concepts: List<Concept>): Problem = saveProblem(concepts, integration = true)

    private fun saveProblem(concepts: List<Concept>, integration: Boolean): Problem =
        problemRepository.save(
            Problem(
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "질문 ${concepts.joinToString { it.name }}",
                concepts = concepts,
                integration = integration,
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        )
}
