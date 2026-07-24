package watson.bytecs.admin.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.problem.domain.Difficulty
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 관리자 통계 페이지가 쓰는 테스터 지표를 집계한다.
 * 집계 자체는 저장소의 @Query가 담당하므로, 서비스는 퍼널 지표·참고 값·난이도 지표를 한 읽기 트랜잭션에서 모아 읽기 모델로 조립한다.
 * 난이도 지표는 그룹 쿼리 결과(빈 그룹은 없음)를 모든 난이도 축에 대해 0으로 채워 표가 항상 고정 행 수를 갖게 한다.
 */
@Service
@Transactional(readOnly = true)
class AdminStatsService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val interviewSessionRepository: InterviewSessionRepository,
    private val clock: Clock,
) {

    /**
     * [from]·[to]가 모두 있으면 그 날짜 구간으로 4개 퍼널 지표(풀이 시작·완료·추가 학습·면접)를 집계하고, 없으면 전체 기간으로 집계한다.
     * 구간은 KST(clock.zone) 기준 [from 00:00, to+1 00:00) 반개구간으로 이벤트 시각(started/completed/judgedAt)에 건다.
     * 선호 난이도 분포·정답 공개율·규모 값은 '현재 상태' 스냅샷이라 시점 개념이 없어 항상 전체 기간이다.
     */
    fun collectTesterMetrics(from: LocalDate? = null, to: LocalDate? = null): TesterMetrics {
        val range = periodRange(from, to)
        return TesterMetrics(
            startedUserCount = range?.let { sessionRepository.countUsersStartedBetween(it.first, it.second) }
                ?: sessionRepository.countUsersStarted(),
            completedUserCount = range?.let { sessionRepository.countUsersCompletedBetween(it.first, it.second) }
                ?: sessionRepository.countUsersCompleted(),
            studiedMoreUserCount = range?.let {
                sessionRepository.countUsersStudiedMoreAfterCompletionBetween(it.first, it.second)
            } ?: sessionRepository.countUsersStudiedMoreAfterCompletion(),
            interviewAnsweredUserCount = range?.let {
                interviewSessionRepository.countUsersAnsweredBetween(it.first, it.second)
            } ?: interviewSessionRepository.countUsersAnswered(),
            totalUserCount = userRepository.count(),
            totalSessionCount = sessionRepository.count(),
            preferredDifficultyDistribution = buildPreferredDifficultyDistribution(),
            difficultyRevealRates = buildDifficultyRevealRates(),
        )
    }

    /**
     * 날짜 구간을 [시작, 끝) Instant 반개구간으로 바꾼다. 둘 중 하나라도 없으면 전체 기간(null).
     * from>to로 뒤집어 들어와도 관대하게 min/max로 정렬한다(관리자 입력 실수 방어).
     */
    private fun periodRange(from: LocalDate?, to: LocalDate?): Pair<Instant, Instant>? {
        if (from == null || to == null) {
            return null
        }
        val zone = clock.zone
        val start = minOf(from, to).atStartOfDay(zone).toInstant()
        val endExclusive = maxOf(from, to).plusDays(1).atStartOfDay(zone).toInstant()
        return start to endExclusive
    }

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
