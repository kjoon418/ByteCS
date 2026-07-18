package watson.bytecs.admin

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.security.crypto.password.PasswordEncoder
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserRole
import watson.bytecs.account.infrastructure.UserRepository

class AdminAccountBootstrapTest {

    private val userRepository = mock(UserRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)

    private fun bootstrap(email: String, password: String): AdminAccountBootstrap =
        AdminAccountBootstrap(email, password, userRepository, passwordEncoder)

    @Test
    fun `설정이 모두 비어 있으면 아무것도 하지 않는다`() {
        bootstrap("", "").run(null)

        verifyNoInteractions(userRepository)
    }

    @Test
    fun `이메일과 비밀번호 중 하나만 설정되면 기동을 중단한다`() {
        assertThatThrownBy { bootstrap(ADMIN_EMAIL, "").run(null) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage(AdminAccountBootstrap.PARTIAL_CONFIG_MESSAGE)
    }

    @Test
    fun `이메일 형식이 잘못되면 기동을 중단한다`() {
        assertThatThrownBy { bootstrap("not-an-email", ADMIN_PASSWORD).run(null) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage(Email.INVALID_FORMAT_MESSAGE)
    }

    @Test
    fun `해당 이메일의 계정이 없으면 인코딩된 비밀번호로 관리자를 생성한다`() {
        // given
        given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(null)
        given(passwordEncoder.encode(ADMIN_PASSWORD)).willReturn(ENCODED_PASSWORD)

        // when
        bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD).run(null)

        // then
        val captor = ArgumentCaptor.forClass(User::class.java)
        verify(userRepository).save(captor.capture())
        val savedAdmin = captor.value
        assertThat(savedAdmin.role).isEqualTo(UserRole.ADMIN)
        assertThat(savedAdmin.email).isEqualTo(ADMIN_EMAIL)
        assertThat(savedAdmin.passwordHash).isEqualTo(ENCODED_PASSWORD)
    }

    @Test
    fun `같은 이메일의 관리자가 이미 있으면 생성하지 않는다`() {
        // given
        val existingAdmin = User.createAdmin(Email(ADMIN_EMAIL), ENCODED_PASSWORD)
        given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(existingAdmin)

        // when
        bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD).run(null)

        // then
        verify(userRepository, never()).save(any())
    }

    @Test
    fun `같은 이메일이 일반 계정으로 존재하면 기동을 중단한다`() {
        // given
        val existingMember = User.createMember(Email(ADMIN_EMAIL), ENCODED_PASSWORD)
        given(userRepository.findByEmail(ADMIN_EMAIL)).willReturn(existingMember)

        // when and then
        assertThatThrownBy { bootstrap(ADMIN_EMAIL, ADMIN_PASSWORD).run(null) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage(AdminAccountBootstrap.EMAIL_ALREADY_TAKEN_MESSAGE)
    }

    private companion object {
        const val ADMIN_EMAIL = "admin@bytecs.dev"
        const val ADMIN_PASSWORD = "admin-password-1234"
        const val ENCODED_PASSWORD = "{bcrypt}encoded"
    }
}
