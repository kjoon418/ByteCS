package watson.bytecs.account.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 사용자 학습 설정. 현재는 일일 세션 분량(dailySessionSize)만 가진다.
 */
@Embeddable
data class UserSettings(
    @Column(name = "daily_session_size", nullable = false)
    val dailySessionSize: Int,
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
