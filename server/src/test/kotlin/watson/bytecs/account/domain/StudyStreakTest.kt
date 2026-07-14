package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StudyStreakTest {

    private val today: LocalDate = LocalDate.of(2026, 7, 14)

    @Test
    fun 초기_스트릭은_0이고_학습일이_없다() {
        val streak = StudyStreak.initial()

        assertThat(streak.count).isEqualTo(0)
        assertThat(streak.lastStudyDate).isNull()
    }

    @Test
    fun 첫_학습이면_1로_시작한다() {
        val updated = StudyStreak.initial().record(today)

        assertThat(updated.count).isEqualTo(1)
        assertThat(updated.lastStudyDate).isEqualTo(today)
    }

    @Test
    fun 같은_날_다시_기록해도_변화가_없다() {
        val once = StudyStreak.initial().record(today)

        val twice = once.record(today)

        assertThat(twice.count).isEqualTo(1)
        assertThat(twice.lastStudyDate).isEqualTo(today)
    }

    @Test
    fun 어제_학습했으면_연속으로_이어_1_증가한다() {
        val yesterday = StudyStreak(count = 3, lastStudyDate = today.minusDays(1))

        val updated = yesterday.record(today)

        assertThat(updated.count).isEqualTo(4)
        assertThat(updated.lastStudyDate).isEqualTo(today)
    }

    @Test
    fun 이틀_이상_공백이_있으면_1로_리셋한다() {
        val twoDaysAgo = StudyStreak(count = 5, lastStudyDate = today.minusDays(2))

        val updated = twoDaysAgo.record(today)

        assertThat(updated.count).isEqualTo(1)
        assertThat(updated.lastStudyDate).isEqualTo(today)
    }
}
