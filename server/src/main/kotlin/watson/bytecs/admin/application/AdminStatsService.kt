package watson.bytecs.admin.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 관리자 통계 페이지가 쓰는 테스터 지표를 집계한다.
 * 집계 자체는 저장소의 @Query가 담당하므로, 서비스는 세 지표와 참고 값을 한 읽기 트랜잭션에서 모아 읽기 모델로 조립한다.
 */
@Service
@Transactional(readOnly = true)
class AdminStatsService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
) {

    fun collectTesterMetrics(): TesterMetrics = TesterMetrics(
        startedUserCount = sessionRepository.countUsersStarted(),
        completedUserCount = sessionRepository.countUsersCompleted(),
        studiedMoreUserCount = sessionRepository.countUsersStudiedMoreAfterCompletion(),
        totalUserCount = userRepository.count(),
        totalSessionCount = sessionRepository.count(),
    )
}
