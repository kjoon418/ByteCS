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
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType
import watson.bytecs.problem.infrastructure.ConceptRepository
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.infrastructure.SessionRepository
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * M1 회귀: 같은 사용자의 세션 '생성' 경합이 사용자 행 비관적 잠금으로 직렬화되어 중복 세션이 생기지 않는지 검증한다.
 * 유니크 제약을 제거한 뒤(D6·D9) 조회-후-생성만으로는 동시 요청 둘이 각자 세션을 만들 수 있으므로, 잠금이 그걸 막아야 한다.
 * 서비스가 메서드마다 @Transactional로 스레드별 트랜잭션을 열므로, 서비스 직접 호출로 실제 경합을 재현한다
 * (CountDownLatch로 두 스레드의 동시 진입을 보장해 플레이키하지 않게 한다).
 */
@SpringBootTest
class SessionConcurrencyIntegrationTest(
    @Autowired private val sessionService: SessionService,
    @Autowired private val sessionRepository: SessionRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val conceptRepository: ConceptRepository,
    @Autowired private val problemRepository: ProblemRepository,
) {

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
        problemRepository.deleteAll()
        conceptRepository.deleteAll()
        userRepository.deleteAll()

        val concept = conceptRepository.save(Concept("개념"))
        problemRepository.save(
            Problem(
                // 서빙 게이트: 배정 후보가 되도록 승인 상태로 시드한다.
                approvalStatus = ApprovalStatus.APPROVED,
                questionText = "질문",
                concepts = listOf(concept),
                acceptableAnswers = setOf("정답"),
                representativeAnswer = "정답",
                type = ProblemType.DEFINITION_RECALL,
                difficulty = Difficulty.EASY,
            ),
        )
        userId = userRepository.save(User.createGuest()).id
    }

    @Test
    fun `같은 사용자의 오늘 세션 동시 생성은 하나만 만든다`() {
        runConcurrently(2) { sessionService.getOrCreateToday(userId) }

        // 잠금이 없으면 둘 다 '없음'을 보고 각자 INSERT해 2가 된다. 잠금 직렬화로 하나만 생긴다.
        assertThat(sessionRepository.count()).isEqualTo(1)
    }

    @Test
    fun `완료 상태에서 조금 더 풀기 동시 요청은 새 세션을 하나만 만든다`() {
        // 오늘 세션을 하나 만들어 완료시킨다(승인 문제 1개라 세션도 한 칸 → 정답 한 번으로 완료).
        sessionService.getOrCreateToday(userId)
        sessionService.submitAnswer(userId, AnswerText("정답"))
        assertThat(sessionRepository.count()).isEqualTo(1)

        runConcurrently(2) { sessionService.getOrCreateNext(userId) }

        // 완료 세션 1 + 신규 1 = 2. 잠금이 없으면 둘 다 새 세션을 만들어 3이 된다.
        assertThat(sessionRepository.count()).isEqualTo(2)
    }

    /**
     * [threads]개 스레드가 [action]을 동시에 실행하도록 CountDownLatch로 출발선을 맞춘다.
     * 모든 스레드가 진입 직전까지 대기(ready)했다가 한 번에 출발(go)해 실제 경합을 만든다.
     * 잠금 직렬화 경로에서는 두 요청 모두 성공해야 하므로, 스레드에서 던져진 예외가 하나라도 있으면 실패로 본다.
     */
    private fun runConcurrently(threads: Int, action: () -> Unit) {
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        val futures = (1..threads).map {
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    action()
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }

        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(errors).isEmpty()
    }
}
