package watson.bytecs.account.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import watson.bytecs.account.domain.UserRole

class JwtTokenProviderTest {

    // HS256에 필요한 최소 256비트(32바이트) 이상 길이의 테스트용 키.
    private val provider = JwtTokenProvider(
        secret = "bytecs-test-secret-key-must-be-at-least-32-bytes-long",
        expirationSeconds = 3600,
    )

    @Test
    fun 발급한_토큰을_파싱하면_식별자와_역할이_복원된다() {
        val token = provider.issue(userId = 42, role = UserRole.MEMBER)

        val principal = provider.parse(token)

        assertThat(principal.userId).isEqualTo(42)
        assertThat(principal.role).isEqualTo(UserRole.MEMBER)
    }

    @Test
    fun 변조된_토큰을_파싱하면_예외를_던진다() {
        val token = provider.issue(userId = 1, role = UserRole.GUEST)
        val tampered = token.dropLast(1) + if (token.last() == 'a') 'b' else 'a'

        assertThatThrownBy { provider.parse(tampered) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun 형식이_아닌_토큰을_파싱하면_예외를_던진다() {
        assertThatThrownBy { provider.parse("garbage-token") }
            .isInstanceOf(Exception::class.java)
    }
}
