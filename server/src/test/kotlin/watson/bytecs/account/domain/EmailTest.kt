package watson.bytecs.account.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EmailTest {

    @Nested
    inner class 생성_시점에_정규화한다 {

        @Test
        fun 앞뒤_공백을_제거하고_소문자로_바꾼다() {
            val email = Email("  User@ByteCS.Dev  ")

            assertThat(email.value).isEqualTo("user@bytecs.dev")
        }

        @Test
        fun 정규화_결과가_같으면_동등하다() {
            assertThat(Email("USER@bytecs.dev")).isEqualTo(Email("user@bytecs.dev"))
        }
    }

    @Nested
    inner class 생성_시점에_형식을_검증한다 {

        @Test
        fun 형식이_올바르지_않으면_예외를_던진다() {
            assertThatThrownBy { Email("not-an-email") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun 빈_문자열이면_예외를_던진다() {
            assertThatThrownBy { Email("   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
