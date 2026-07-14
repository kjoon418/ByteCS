package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserSettingsTest {

    @Test
    fun 기본값은_기본_세션_분량을_가진다() {
        assertThat(UserSettings.default().dailySessionSize)
            .isEqualTo(UserSettings.DEFAULT_DAILY_SESSION_SIZE)
    }

    @Test
    fun 허용_범위의_경계값은_생성된다() {
        assertThat(UserSettings(UserSettings.MINIMUM).dailySessionSize).isEqualTo(UserSettings.MINIMUM)
        assertThat(UserSettings(UserSettings.MAXIMUM).dailySessionSize).isEqualTo(UserSettings.MAXIMUM)
    }

    @Test
    fun 최소값_미만이면_예외를_던진다() {
        assertThatThrownBy { UserSettings(UserSettings.MINIMUM - 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 최대값_초과면_예외를_던진다() {
        assertThatThrownBy { UserSettings(UserSettings.MAXIMUM + 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
