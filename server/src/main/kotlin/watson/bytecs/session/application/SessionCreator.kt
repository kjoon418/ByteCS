package watson.bytecs.session.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.session.domain.Session
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import java.time.LocalDate
import kotlin.random.Random

/**
 * 오늘 세션의 '생성(INSERT)'만 별도 트랜잭션으로 격리해 책임지는 협력자.
 *
 * get-or-create의 경합에서, 저장 실패(유니크 제약 위반)의 롤백이 호출자(get) 트랜잭션을 오염시키면 안 된다.
 * 같은 트랜잭션 안에서 flush가 실패하면 그 트랜잭션이 rollback-only로 표시되어, 이후 재조회가 성공해도
 * 최종 커밋이 UnexpectedRollbackException으로 터진다(→ 500). 그래서 INSERT를 REQUIRES_NEW로 분리해,
 * 실패 시 '이 새 트랜잭션만' 롤백되고 호출자 트랜잭션은 깨끗이 유지되게 한다(호출자가 잡아 재조회 가능).
 * 자기호출은 프록시를 타지 않으므로, 반드시 별도 빈으로 분리해 주입받아 호출해야 REQUIRES_NEW가 적용된다.
 */
@Component
class SessionCreator(
    private val sessionRepository: SessionRepository,
    private val problemRepository: ProblemRepository,
    private val userRepository: UserRepository,
    private val reviewService: ReviewService,
    // 푼/배정 문제 풀은 세션 단독이 아니라 세션 ∪ 추가 학습의 합집합을 본다(설계 §1.2).
    private val learningHistory: LearningHistory,
    // 새 개념 문제를 무작위로 뽑는 데 쓴다. 기본은 Random.Default이고, 테스트에서 시드 고정 Random을 주입해
    // 셔플 결과를 결정적으로 검증한다(운영에선 사용자마다·매일 다른 세션을 위해 매번 새 순서를 뽑는다).
    private val random: Random = Random.Default,
) {

    /**
     * 오늘 세션을 새 트랜잭션에서 배정·저장한다.
     * saveAndFlush로 유니크 제약 위반을 이 트랜잭션 경계 안에서 즉시 표출해,
     * 경합 시 호출자가 DataIntegrityViolationException을 받아 재조회로 복구할 수 있게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createInNewTransaction(userId: Long, today: LocalDate): Session {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }
        val problemIds = assignProblemIds(user, today)

        return sessionRepository.saveAndFlush(Session.assign(userId, today, problemIds))
    }

    /**
     * 오늘 세션에 배정할 본 문제 id를 정한다. 복습(기능 3)을 새 개념 문제와 인터리빙한다.
     *  1. 복습 편입: 복습 시점이 도래한 개념의 복습 문제를 도래 순으로 먼저 채운다(상한 없음 — 세션 분량까지 채울 수 있다).
     *  2. 남은 칸: 새 개념 문제를 채운다 — 아직 안 푼 문제가 있으면 그중에서, 없으면 전체 풀 폴백(반복 허용).
     *     새 개념 후보는 매번 [random]으로 셔플해 무작위로 배정한다(모든 사용자가 같은 순서로 같은 문제를 받지 않게 — QA #6).
     * 중복 problemId는 선착순으로 제거하고(복습 우선), 세션 분량 초과분은 도래 순 우선으로 잘린다.
     * 정책: 복습은 도래 순으로 우선 배치하고, 남은 칸의 새 개념 문제는 무작위로 뽑는다.
     * (같은 학습 상태·같은 시드면 같은 세션이 나오지만, 운영의 Random.Default에선 매번 다른 순서가 나온다.)
     */
    private fun assignProblemIds(user: User, today: LocalDate): List<Long> {
        val size = user.settings.dailySessionSize
        val allProblemIds = problemRepository.findAllIdsOrderByIdAsc()
        val poolIds = allProblemIds.toSet()

        // 1) 복습 편입: 도래 개념의 복습 문제(회수된 후보는 건너뜀). 배정 이력은 유도형 예외 판정에 쓴다(세션 ∪ 추가 학습).
        val assignedProblemIds = learningHistory.findAssignedProblemIds(user.id)
        val reviewProblemIds = reviewService.selectDueReviewProblemIds(user.id, today, assignedProblemIds, poolIds)

        // 2) 남은 칸을 채울 새 개념 후보: 아직 안 푼 문제 우선, 없으면 전체 풀 폴백. 두 경우 모두 무작위로 셔플한다.
        //    이미 푼 문제 판단도 세션 ∪ 추가 학습 합집합으로 본다 — 추가 학습에서 푼 문제를 세션이 새 개념으로 다시 내지 않게.
        val solvedProblemIds = learningHistory.findSolvedProblemIds(user.id)
        val unseenProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        val newConceptCandidates =
            (if (unseenProblemIds.isNotEmpty()) unseenProblemIds else allProblemIds).shuffled(random)

        // 복습 먼저 배치 → 남은 칸을 새 개념으로. LinkedHashSet으로 선착순 중복 제거·순서 보존, 분량에서 절단한다.
        val chosen = LinkedHashSet<Long>()
        for (problemId in reviewProblemIds) {
            if (chosen.size >= size) break
            chosen.add(problemId)
        }
        for (problemId in newConceptCandidates) {
            if (chosen.size >= size) break
            chosen.add(problemId)
        }

        if (chosen.isEmpty()) {
            // 콘텐츠 자체가 하나도 없어 어떤 세션도 만들 수 없는 경우.
            throw ProblemNotFoundException.noneAvailable()
        }
        return chosen.toList()
    }
}
