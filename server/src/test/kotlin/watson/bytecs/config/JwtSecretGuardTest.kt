package watson.bytecs.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtSecretGuardTest {

    private val customSecret = "an-injected-strong-secret-key-0123456789-abcdefghij"

    @Test
    fun 기본_시크릿을_운영_프로파일에서_쓰면_금지한다() {
        val forbidden = JwtSecretGuard.isForbiddenSecret(
            activeProfiles = listOf("prod"),
            secret = JwtSecretGuard.DEFAULT_DEV_SECRET,
        )

        assertThat(forbidden).isTrue()
    }

    @Test
    fun 기본_시크릿을_local_프로파일에서_쓰면_허용한다() {
        val forbidden = JwtSecretGuard.isForbiddenSecret(
            activeProfiles = listOf("local"),
            secret = JwtSecretGuard.DEFAULT_DEV_SECRET,
        )

        assertThat(forbidden).isFalse()
    }

    @Test
    fun 기본_시크릿을_test_프로파일에서_쓰면_허용한다() {
        val forbidden = JwtSecretGuard.isForbiddenSecret(
            activeProfiles = listOf("test"),
            secret = JwtSecretGuard.DEFAULT_DEV_SECRET,
        )

        assertThat(forbidden).isFalse()
    }

    @Test
    fun 주입된_커스텀_시크릿은_운영_프로파일에서도_허용한다() {
        val forbidden = JwtSecretGuard.isForbiddenSecret(
            activeProfiles = listOf("prod"),
            secret = customSecret,
        )

        assertThat(forbidden).isFalse()
    }
}
