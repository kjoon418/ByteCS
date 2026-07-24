package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.Normalizer

class AnswerTextTest {

    @Nested
    inner class 생성_시점에_정규화한다 {

        @Test
        fun 앞뒤_공백을_제거한다() {
            val answer = AnswerText("  스택  ")

            assertThat(answer.value).isEqualTo("스택")
        }

        @Test
        fun 대문자를_소문자로_바꾼다() {
            val answer = AnswerText("Stack")

            assertThat(answer.value).isEqualTo("stack")
        }

        @Test
        fun 내부_공백을_전부_제거한다() {
            val answer = AnswerText("해시   충돌")

            assertThat(answer.value).isEqualTo("해시충돌")
        }

        @Test
        fun 띄어쓰기_여부와_무관하게_같은_답으로_본다() {
            // 오너 결정(2026-07-24): 띄어쓰기가 맞았는지는 정답 여부와 무관하다.
            assertThat(AnswerText("해시충돌")).isEqualTo(AnswerText("해시 충돌"))
            assertThat(AnswerText("가상 메모리")).isEqualTo(AnswerText("가상메모리"))
        }

        @Test
        fun 자모_분해형_한글을_조합형으로_정규화한다() {
            // given: macOS/iOS가 보낼 수 있는 자모 분해형(NFD) "스택"
            val decomposed = Normalizer.normalize("스택", Normalizer.Form.NFD)

            // when
            val answer = AnswerText(decomposed)

            // then: 조합형(NFC)으로 입력한 값과 동등해야 한다.
            assertThat(answer).isEqualTo(AnswerText("스택"))
        }
    }

    @Nested
    inner class 생성_시점에_값을_검증한다 {

        @Test
        fun 빈_문자열이면_예외를_던진다() {
            assertThatThrownBy { AnswerText("") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage(AnswerText.BLANK_MESSAGE)
        }

        @Test
        fun 공백만_있으면_예외를_던진다() {
            assertThatThrownBy { AnswerText("   ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage(AnswerText.BLANK_MESSAGE)
        }
    }

    @Test
    fun 정규화_결과가_같으면_동등하다() {
        val fromRaw = AnswerText("  TCP ")
        val fromNormalized = AnswerText("tcp")

        assertThat(fromRaw).isEqualTo(fromNormalized)
    }
}
