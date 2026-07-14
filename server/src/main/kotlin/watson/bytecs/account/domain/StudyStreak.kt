package watson.bytecs.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

/**
 * 연속 학습 스트릭. 마지막으로 학습한 날짜와 연속 일수를 가진다.
 * 값 객체(불변)로 두어, 기록 갱신은 항상 새 스트릭을 만들어 교체한다(전이 규칙을 한곳에 응집).
 * 기준일(today)은 호출자가 주입한 KST 날짜다 — 도메인은 시계를 직접 읽지 않는다.
 */
@Embeddable
data class StudyStreak(
    @Column(name = "streak_count", nullable = false)
    val count: Int,

    @Column(name = "last_study_date")
    val lastStudyDate: LocalDate?,
) {
    /**
     * 오늘 학습을 기록해 갱신된 스트릭을 돌려준다.
     *  - 같은 날 다시 기록하면 변화 없음(하루 여러 세션도 1일로 집계).
     *  - 어제 학습했다면 연속으로 이어져 +1.
     *  - 그 외(공백이 있었거나 첫 학습)면 1로 리셋.
     */
    fun record(today: LocalDate): StudyStreak = when (lastStudyDate) {
        today -> this
        today.minusDays(1) -> StudyStreak(count + 1, today)
        else -> StudyStreak(1, today)
    }

    companion object {
        /** 아직 한 번도 학습하지 않은 초기 스트릭. */
        fun initial(): StudyStreak = StudyStreak(0, null)
    }
}
