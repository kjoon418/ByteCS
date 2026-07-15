package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MisconceptionHintTest {

    @Nested
    inner class 예상_오답과_정규화_후_대조한다 {

        @Test
        fun 정규화_후_일치하면_매칭이다() {
            val hint = MisconceptionHint(setOf("프로세스", "process"), "메시지")

            assertThat(hint.matches(AnswerText("  Process "))).isTrue()
        }

        @Test
        fun 예상_오답에_없으면_매칭되지_않는다() {
            val hint = MisconceptionHint(setOf("프로세스"), "메시지")

            assertThat(hint.matches(AnswerText("커널"))).isFalse()
        }
    }

    @Nested
    inner class 생성_시점에_검증한다 {

        @Test
        fun 예상_오답_집합이_비어_있으면_예외를_던진다() {
            assertThatThrownBy { MisconceptionHint(emptySet(), "메시지") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("예상 오답 집합은 비어 있을 수 없습니다.")
        }

        @Test
        fun 예상_오답에_빈_표기가_섞이면_예외를_던진다() {
            assertThatThrownBy { MisconceptionHint(setOf("프로세스", " "), "메시지") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("예상 오답 표기는 비어 있을 수 없습니다.")
        }

        @Test
        fun 교정_메시지가_비어_있으면_예외를_던진다() {
            assertThatThrownBy { MisconceptionHint(setOf("프로세스"), " ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("오답 교정 메시지는 비어 있을 수 없습니다.")
        }
    }
}
