package watson.bytecs.session.application

import jakarta.persistence.EntityManagerFactory
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserSettings
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.domain.ConceptMastery
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.LocalDate

/**
 * 연결 문제 하드 게이트(계획 §3.2 · DI7)의 N+1 회귀를 하이버네이트 Statistics로 검증한다.
 * 게이트가 후보별로 '구성 개념을 모두 학습했는가'를 개별 조회(N+1)하면 실행 SQL 문 수가 연결 문제 후보 수에
 * 비례해 늘어난다 — 보유 개념·후보 개념 매핑을 각각 1회로 접는 구현이 이를 고정 개수로 유지해야 한다.
 *
 * 지표로 [org.hibernate.stat.Statistics.getPrepareStatementCount]를 쓴다(`queryExecutionCount`가 아님 —
 * 지연 컬렉션 초기화가 내부적으로 발행하는 SQL을 놓쳐 이런 N+1을 못 잡기 때문. 기존 관례
 * [watson.bytecs.study.application.CategoryHistoryServiceQueryCountTest] 참고).
 * 후보가 모두 게이트를 통과하도록 사용자에게 전 개념 숙련도를 심고, 세션 분량을 1로 고정해 저장 문 수도 후보 수와 무관하게 둔다.
 */
@SpringBootTest
class SessionCreatorConnectionGateQueryCountTest(
    @Autowired private val sessionCreator: SessionCreator,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
    @Autowired private val conceptMasteryRepository: ConceptMasteryRepository,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
) {

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        conceptMasteryRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        // 세션 분량 1: 후보 수가 달라도 저장되는 본 문제는 1개로 같아, 저장 SQL 문 수가 후보 수에 영향받지 않는다.
        userId = userRepository.save(User.createGuest().apply { updateSettings(UserSettings(1)) }).id

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.isStatisticsEnabled = true
    }

    @Test
    fun `세션 배정 SQL 문 수는 연결 문제 후보 수와 무관하게 고정된다`() {
        val statementCountForOneCandidate = statementCountForConnectedCandidates(connectedProblemCount = 1)
        val statementCountForFiveCandidates = statementCountForConnectedCandidates(connectedProblemCount = 5)

        // 게이트가 후보별 개별 조회(N+1)로 퇴화했다면 후보 수만큼 SQL 문이 늘어나 두 값이 달라진다.
        assertThat(statementCountForFiveCandidates).isEqualTo(statementCountForOneCandidate)
    }

    /**
     * [connectedProblemCount]개의 연결 문제(각 개념 2개)를 시드하고, 사용자에게 전 개념 숙련도를 심어 모두 게이트를 통과시킨 뒤,
     * 오늘 세션 배정 1회의 SQL 문 수를 돌려준다. 세션 분량이 1이라 배정 결과는 후보 수와 무관하게 1문제다.
     */
    private fun statementCountForConnectedCandidates(connectedProblemCount: Int): Long {
        sessionRepository.deleteAll()
        conceptMasteryRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()

        repeat(connectedProblemCount) { index -> seedUnlockedConnectedProblem(index) }

        val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
        statistics.clear()

        sessionCreator.create(userId, LocalDate.now())

        return statistics.prepareStatementCount
    }

    /**
     * 개념 2개에 태깅된 **지정 연결 문제(integration=true)**를 저장하고, 그 두 개념의 숙련도 행을 사용자에게 심어
     * 게이트를 통과 상태로 만든다. 지정 문제라야 게이트 쿼리([findConceptIdsOfIntegrationProblems])가 후보로 잡아
     * 개념 조회 비용이 발생하므로, N+1 여부를 이 픽스처로 드러낸다.
     * 숙련도의 다음 복습 시점은 미래(오늘+간격)라 복습으로 도래하지 않는다 — 이 문제는 오직 새 개념 후보로만 잡힌다.
     */
    private fun seedUnlockedConnectedProblem(index: Int) {
        val today = LocalDate.now()
        val first = conceptRepository.save(Concept("게이트개념A$index"))
        val second = conceptRepository.save(Concept("게이트개념B$index"))
        problemRepository.save(
            Problem(
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "연결문제$index",
                concepts = listOf(first, second),
                integration = true,
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
            ),
        )
        listOf(first.id, second.id).forEach { conceptId ->
            conceptMasteryRepository.save(
                ConceptMastery.firstSolve(userId, conceptId, MasterySignal.UNAIDED, today, 0L),
            )
        }
    }
}
