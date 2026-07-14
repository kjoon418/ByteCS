package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class UserTest {

    @Nested
    inner class 게스트를_생성한다 {

        @Test
        fun 역할은_GUEST이고_이메일과_비밀번호가_없다() {
            val guest = User.createGuest()

            assertThat(guest.role).isEqualTo(UserRole.GUEST)
            assertThat(guest.isMember).isFalse()
            assertThat(guest.email).isNull()
            assertThat(guest.passwordHash).isNull()
        }

        @Test
        fun 설정은_기본값을_가진다() {
            val guest = User.createGuest()

            assertThat(guest.settings).isEqualTo(UserSettings.default())
        }
    }

    @Nested
    inner class 게스트를_회원으로_승격한다 {

        @Test
        fun 역할과_이메일과_비밀번호가_채워진다() {
            val guest = User.createGuest()

            guest.promoteToMember(Email("member@bytecs.dev"), "hashed")

            assertThat(guest.role).isEqualTo(UserRole.MEMBER)
            assertThat(guest.isMember).isTrue()
            assertThat(guest.email).isEqualTo("member@bytecs.dev")
            assertThat(guest.passwordHash).isEqualTo("hashed")
        }

        @Test
        fun 이미_회원이면_예외를_던진다() {
            val member = User.createMember(Email("member@bytecs.dev"), "hashed")

            assertThatThrownBy { member.promoteToMember(Email("other@bytecs.dev"), "hashed2") }
                .isInstanceOf(InvalidUserStateException::class.java)
        }
    }
}
