package watson.bytecs.session.application

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
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
        val problemIds = assignProblemIds(user)

        return sessionRepository.saveAndFlush(Session.assign(userId, today, problemIds))
    }

    /**
     * 오늘 세션에 배정할 본 문제 id를 정한다(MVP: '새 개념만' = 아직 정답으로 통과하지 못한 문제 우선).
     *  - 아직 풀지 않은 문제가 있으면 그 중에서 id 오름차순으로 분량만큼(부족하면 있는 만큼) 배정한다.
     *  - 모두 풀어 새로 배정할 게 없으면, 세션이 비지 않도록 전체 풀에서 폴백 배정한다(반복 허용 — 복습 편입은 기능 3 소관).
     * 배정 순서는 id 오름차순으로 결정적이라, 같은 상태면 항상 같은 세션이 만들어진다.
     */
    private fun assignProblemIds(user: User): List<Long> {
        val size = user.settings.dailySessionSize
        val solvedProblemIds = sessionRepository.findSolvedProblemIds(user.id).toSet()
        val allProblemIds = problemRepository.findAllIdsOrderByIdAsc()

        val unseenProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        val chosen = if (unseenProblemIds.isNotEmpty()) {
            unseenProblemIds.take(size)
        } else {
            allProblemIds.take(size)
        }

        if (chosen.isEmpty()) {
            // 콘텐츠 자체가 하나도 없어 어떤 세션도 만들 수 없는 경우.
            throw ProblemNotFoundException.noneAvailable()
        }
        return chosen
    }
}
