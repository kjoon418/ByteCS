package watson.bytecs.session.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.session.domain.Session
import java.time.Instant
import java.time.LocalDate

/**
 * 테스터 지표 집계 쿼리(진입·완료·완료 후 추가 학습 DISTINCT 사용자 수)를 실제 DB 왕복으로 검증한다.
 * 특히 지표 3(완료 후 추가 학습)은 같은 (user_id, session_date) 안에서 완료 세션보다 나중에 시작된 세션을
 * 가려내야 하므로, 완료→다음 세션 시작·완료만·완료 없이 세션 2개를 각각 다른 사용자로 세워 구분을 검증한다.
 */
@SpringBootTest
class SessionMetricsRepositoryTest(
    @Autowired private val sessionRepository: SessionRepository,
) {

    @BeforeEach
    fun setUp() {
        sessionRepository.deleteAll()
    }

    @Test
    fun `진입 지표는 시작 시각이 기록된 세션을 가진 사용자만 센다`() {
        // given
        startedSession(userId = STARTED_ONLY_USER)
        assignedSession(userId = NEVER_STARTED_USER) // 배정만 하고 진입하지 않은 사용자는 세지 않는다.

        // when and then
        assertThat(sessionRepository.countUsersStarted()).isEqualTo(1)
    }

    @Test
    fun `완료 지표는 완료된 세션을 가진 사용자만 센다`() {
        // given
        completedSession(userId = COMPLETED_USER)
        startedSession(userId = STARTED_ONLY_USER) // 진입했지만 완료하지 않은 사용자는 세지 않는다.

        // when and then
        assertThat(sessionRepository.countUsersCompleted()).isEqualTo(1)
    }

    @Test
    fun `추가 학습 지표는 완료 후 같은 날 나중 세션에 진입한 사용자만 센다`() {
        // given
        // 완료 후 '조금 더 풀기'로 나중 세션까지 시작한 사용자 — 지표 3에 해당한다.
        completedSession(userId = STUDIED_MORE_USER)
        startedSession(userId = STUDIED_MORE_USER)

        // 완료만 하고 추가 세션이 없는 사용자 — 지표 3에 해당하지 않는다.
        completedSession(userId = COMPLETED_USER)

        // 완료 없이 같은 날 세션 두 개를 시작한 사용자 — 완료 후가 아니므로 지표 3에 해당하지 않는다.
        startedSession(userId = TWO_SESSIONS_NO_COMPLETE_USER)
        startedSession(userId = TWO_SESSIONS_NO_COMPLETE_USER)

        // 완료 후 다음 세션이 배정만 되고 풀이 화면에는 진입하지 않은(started_at null) 사용자 — 아직 추가로 풀지
        // 않았으므로 지표 3에 해당하지 않는다('later.startedAt is not null' 절이 이 사용자를 걸러야 한다).
        completedSession(userId = COMPLETED_THEN_ASSIGNED_ONLY_USER)
        assignedSession(userId = COMPLETED_THEN_ASSIGNED_ONLY_USER)

        // when and then
        assertThat(sessionRepository.countUsersStudiedMoreAfterCompletion()).isEqualTo(1)
    }

    /** 배정만 된(진입·완료 시각 없는) 세션을 저장한다. */
    private fun assignedSession(userId: Long): Session =
        sessionRepository.save(Session.assign(userId, TODAY, listOf(PROBLEM_ID)))

    /** 풀이 화면에 진입한(started_at 기록) 세션을 저장한다. */
    private fun startedSession(userId: Long): Session =
        sessionRepository.save(
            Session.assign(userId, TODAY, listOf(PROBLEM_ID)).apply { markStarted(NOW) },
        )

    /** 완료된(started_at·completed_at 기록, 정답 통과) 세션을 저장한다. */
    private fun completedSession(userId: Long): Session =
        sessionRepository.save(
            Session.assign(userId, TODAY, listOf(PROBLEM_ID)).apply {
                markStarted(NOW)
                recordAttempt(Judgement.CORRECT, AnswerText("정답"))
                markCompleted(NOW)
            },
        )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 7, 14)
        val NOW: Instant = Instant.parse("2026-07-14T00:10:00Z")
        const val PROBLEM_ID = 100L

        const val STARTED_ONLY_USER = 1L
        const val NEVER_STARTED_USER = 2L
        const val COMPLETED_USER = 3L
        const val STUDIED_MORE_USER = 4L
        const val TWO_SESSIONS_NO_COMPLETE_USER = 5L
        const val COMPLETED_THEN_ASSIGNED_ONLY_USER = 6L
    }
}
