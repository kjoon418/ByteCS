package watson.bytecs.admin.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.session.infrastructure.SessionRepository

/**
 * 관리자 통계 페이지가 쓰는 테스터 지표를 집계한다.
 * 집계 자체는 저장소의 @Query가 담당하므로, 서비스는 세 지표·참고 값·난이도 지표를 한 읽기 트랜잭션에서 모아 읽기 모델로 조립한다.
 * 난이도 지표는 그룹 쿼리 결과(빈 그룹은 없음)를 모든 난이도 축에 대해 0으로 채워 표가 항상 고정 행 수를 갖게 한다.
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
        preferredDifficultyDistribution = buildPreferredDifficultyDistribution(),
        difficultyRevealRates = buildDifficultyRevealRates(),
    )

    /** 선호 난이도 분포: 쉬움·보통·어려움·미설정 순으로 항상 4행. 쿼리에 없는 그룹은 0명으로 채운다. */
    private fun buildPreferredDifficultyDistribution(): List<PreferredDifficultyStat> {
        val countByDifficulty = userRepository.countLearnersByPreferredDifficulty()
            .associate { it.difficulty to it.count }
        val tiers = Difficulty.values().map { PreferredDifficultyStat(labelOf(it), countByDifficulty[it] ?: 0) }
        val unset = PreferredDifficultyStat(UNSET_LABEL, countByDifficulty[null] ?: 0)
        return tiers + unset
    }

    /** 난이도별 정답 공개율: 쉬움·보통·어려움 순으로 항상 3행. 푼 문제가 없는 난이도는 0/0(0%)으로 채운다. */
    private fun buildDifficultyRevealRates(): List<DifficultyRevealStat> {
        val rowByDifficulty = sessionRepository.countSolvedItemRevealsByDifficulty()
            .associateBy { it.difficulty }
        return Difficulty.values().map { difficulty ->
            val row = rowByDifficulty[difficulty]
            val solved = row?.solvedCount ?: 0
            val revealed = row?.revealedCount ?: 0
            DifficultyRevealStat(labelOf(difficulty), solved, revealed, revealRatePercent(revealed, solved))
        }
    }

    /** 푼 수가 0이면 공개율은 0으로 둔다(0 나눗셈 방지). 그 외에는 반올림한 백분율. */
    private fun revealRatePercent(revealed: Long, solved: Long): Int =
        if (solved == 0L) 0 else Math.round(revealed * 100.0 / solved).toInt()

    private fun labelOf(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.EASY -> "쉬움"
        Difficulty.MEDIUM -> "보통"
        Difficulty.HARD -> "어려움"
    }

    private companion object {
        const val UNSET_LABEL = "미설정"
    }
}
