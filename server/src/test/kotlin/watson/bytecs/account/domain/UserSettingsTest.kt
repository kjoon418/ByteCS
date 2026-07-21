package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.bytecs.problem.domain.Difficulty

class UserSettingsTest {

    @Test
    fun 기본값은_기본_세션_분량을_가진다() {
        assertThat(UserSettings.default().dailySessionSize)
            .isEqualTo(UserSettings.DEFAULT_DAILY_SESSION_SIZE)
    }

    @Test
    fun 기본값의_선호_난이도는_미설정이다() {
        assertThat(UserSettings.default().preferredDifficulty).isNull()
    }

    @Test
    fun 세션_분량만_바꾸면_선호_난이도는_보존된다() {
        val settings = UserSettings(dailySessionSize = 5, preferredDifficulty = Difficulty.HARD)

        val updated = settings.copy(dailySessionSize = 10)

        assertThat(updated.dailySessionSize).isEqualTo(10)
        assertThat(updated.preferredDifficulty).isEqualTo(Difficulty.HARD)
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
