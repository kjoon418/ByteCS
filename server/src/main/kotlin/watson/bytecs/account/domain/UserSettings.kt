package watson.bytecs.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import watson.bytecs.problem.domain.Difficulty

/**
 * 사용자 학습 설정.
 *  - dailySessionSize: 일일 세션 분량.
 *  - preferredDifficulty: 선호 난이도(가중 출제의 입력). NULL = 미설정 — 현행 균등 무작위 배정을 유지한다(강제 없음·무낙인).
 * 게스트 포함 사용자 소유 설정이며, 값은 언제든 설정 화면에서 변경할 수 있다.
 */
@Embeddable
data class UserSettings(
    @Column(name = "daily_session_size", nullable = false)
    val dailySessionSize: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_difficulty")
    val preferredDifficulty: Difficulty? = null,
) {
    init {
        require(dailySessionSize in MINIMUM..MAXIMUM) {
            "일일 세션 분량은 $MINIMUM 이상 $MAXIMUM 이하여야 합니다. value = $dailySessionSize"
        }
    }

    companion object {
        const val DEFAULT_DAILY_SESSION_SIZE = 5
        const val MINIMUM = 1
        const val MAXIMUM = 50

        fun default(): UserSettings = UserSettings(DEFAULT_DAILY_SESSION_SIZE)
    }
}
