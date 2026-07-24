package watson.bytecs.interview.infrastructure

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import watson.bytecs.interview.domain.InterviewSession
import java.time.Instant
import java.time.LocalDate

/**
 * 테스터 지표 — '면접 문제까지 푼 사용자'(설명을 1개 이상 제출한 DISTINCT 사용자) 집계를 실제 DB 왕복으로 검증한다.
 * 답 제출은 채점 성공([recordGraded])이든 폴백([recordFallback])이든 그 칸의 judgedAt을 채우므로, 이를 '제출함'의 기준으로 쓴다.
 * 세션만 만들고 답을 안 낸 사용자는 세지 않고, 기간판은 제출 시각(judgedAt)으로 거른다.
 */
@SpringBootTest
class InterviewMetricsRepositoryTest(
    @Autowired private val repository: InterviewSessionRepository,
) {

    @BeforeEach
    fun setUp() {
        repository.deleteAll()
    }

    @Test
    fun `면접 지표는 설명을 제출한 사용자만 세고 세션만 만든 사용자는 세지 않는다`() {
        // 채점 성공으로 답을 낸 사용자.
        repository.save(
            InterviewSession.assign(GRADED_USER, DATE, listOf(PROMPT_ID)).apply {
                recordGraded("제 설명입니다", listOf(true), "코멘트", NOW)
            },
        )
        // 폴백(채점 실패)이라도 제출은 했으므로 센다.
        repository.save(
            InterviewSession.assign(FALLBACK_USER, DATE, listOf(PROMPT_ID)).apply {
                recordFallback("제 설명입니다", NOW)
            },
        )
        // 세션만 배정되고 답을 내지 않은 사용자 — 세지 않는다.
        repository.save(InterviewSession.assign(ASSIGNED_ONLY_USER, DATE, listOf(PROMPT_ID)))

        assertThat(repository.countUsersAnswered()).isEqualTo(2)
    }

    @Test
    fun `면접 기간 지표는 제출 시각이 구간 안에 든 사용자만 센다`() {
        repository.save(
            InterviewSession.assign(GRADED_USER, DATE, listOf(PROMPT_ID)).apply {
                recordGraded("설명", listOf(true), "코멘트", NOW)
            },
        )

        assertThat(repository.countUsersAnsweredBetween(DAY_START, NEXT_DAY_START)).isEqualTo(1)
        assertThat(repository.countUsersAnsweredBetween(PAST_START, PAST_END)).isEqualTo(0)
    }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 7, 14)
        val NOW: Instant = Instant.parse("2026-07-14T00:10:00Z")
        val DAY_START: Instant = Instant.parse("2026-07-14T00:00:00Z")
        val NEXT_DAY_START: Instant = Instant.parse("2026-07-15T00:00:00Z")
        val PAST_START: Instant = Instant.parse("2026-07-10T00:00:00Z")
        val PAST_END: Instant = Instant.parse("2026-07-11T00:00:00Z")
        const val PROMPT_ID = 100L

        const val GRADED_USER = 1L
        const val FALLBACK_USER = 2L
        const val ASSIGNED_ONLY_USER = 3L
    }
}
