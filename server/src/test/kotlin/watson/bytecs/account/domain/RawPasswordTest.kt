package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class RawPasswordTest {

    @Test
    fun 최소_길이_이상이면_값을_보관한다() {
        val password = RawPassword("password1")

        assertThat(password.value).isEqualTo("password1")
    }

    @Test
    fun 최소_길이_미만이면_예외를_던진다() {
        assertThatThrownBy { RawPassword("short") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(RawPassword.TOO_SHORT_MESSAGE)
    }

    @Test
    fun toString은_원문을_노출하지_않는다() {
        val password = RawPassword("password1")

        assertThat(password.toString()).doesNotContain("password1")
    }
}
