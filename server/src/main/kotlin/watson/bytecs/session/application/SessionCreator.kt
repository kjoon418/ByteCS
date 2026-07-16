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
import java.time.LocalDate

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
     *  2. 남은 칸: 기존 로직으로 새 개념 문제를 채운다 — 아직 안 푼 문제(id asc)가 있으면 그것, 없으면 전체 풀 폴백(반복 허용).
     * 중복 problemId는 선착순으로 제거하고(복습 우선), 세션 분량 초과분은 도래 순 우선으로 잘린다.
     * 두 축 모두 결정적 순서(도래 순·id asc)라, 같은 학습 상태면 항상 같은 세션이 만들어진다.
     */
    private fun assignProblemIds(user: User, today: LocalDate): List<Long> {
        val size = user.settings.dailySessionSize
        val allProblemIds = problemRepository.findAllIdsOrderByIdAsc()
        val poolIds = allProblemIds.toSet()

        // 1) 복습 편입: 도래 개념의 복습 문제(회수된 후보는 건너뜀). 배정 이력은 유도형 예외 판정에 쓴다.
        val assignedProblemIds = sessionRepository.findAssignedProblemIds(user.id).toSet()
        val reviewProblemIds = reviewService.selectDueReviewProblemIds(user.id, today, assignedProblemIds, poolIds)

        // 2) 남은 칸을 채울 새 개념 후보: 아직 안 푼 문제 우선, 없으면 전체 풀 폴백.
        val solvedProblemIds = sessionRepository.findSolvedProblemIds(user.id).toSet()
        val unseenProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        val newConceptCandidates = if (unseenProblemIds.isNotEmpty()) unseenProblemIds else allProblemIds

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
