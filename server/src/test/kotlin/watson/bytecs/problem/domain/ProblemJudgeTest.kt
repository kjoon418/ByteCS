package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.Normalizer

class ProblemJudgeTest {

    @Nested
    inner class 정확히_일치하면_CORRECT를_반환한다 {

        @Test
        fun 허용답과_동일하면_정답이다() {
            val problem = problemWithAnswers("스택", "stack")

            assertThat(problem.judge(AnswerText("스택"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 대소문자와_앞뒤_공백이_달라도_정규화_후_일치하면_정답이다() {
            val problem = problemWithAnswers("stack")

            assertThat(problem.judge(AnswerText("  Stack "))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 내부_공백이_달라도_정규화_후_일치하면_정답이다() {
            val problem = problemWithAnswers("해시 충돌")

            assertThat(problem.judge(AnswerText("해시   충돌"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 허용답_중_하나라도_일치하면_정답이다() {
            val problem = problemWithAnswers("충돌", "collision")

            assertThat(problem.judge(AnswerText("collision"))).isEqualTo(Judgement.CORRECT)
        }

        @Test
        fun 자모_분해형_한글_입력도_조합형_허용답과_정답이다() {
            val problem = problemWithAnswers("스택")

            val decomposedAnswer = AnswerText(Normalizer.normalize("스택", Normalizer.Form.NFD))

            assertThat(problem.judge(decomposedAnswer)).isEqualTo(Judgement.CORRECT)
        }
    }

    @Nested
    inner class 오탈자_수준으로_가까우면_NEAR_MISS를_반환한다 {

        @Test
        fun 짧은_답에서_편집거리가_1이면_근접이다() {
            // "stack"(길이 5, 임계 1)에서 한 글자가 빠진 "stak".
            val problem = problemWithAnswers("stack")

            assertThat(problem.judge(AnswerText("stak"))).isEqualTo(Judgement.NEAR_MISS)
        }

        @Test
        fun 긴_답에서_편집거리가_2이면_근접이다() {
            // "quicksort"(길이 9, 임계 2)에서 인접 두 글자가 뒤바뀐 "quicksrot".
            val problem = problemWithAnswers("quicksort")

            assertThat(problem.judge(AnswerText("quicksrot"))).isEqualTo(Judgement.NEAR_MISS)
        }

        @Test
        fun 길이_8인_답은_편집거리_2까지_근접이다() {
            // 길이 7→8 경계에서 임계가 1→2로 전환된다. "abstract"(길이 8)의 두 글자 교체.
            val problem = problemWithAnswers("abstract")

            assertThat(problem.judge(AnswerText("abstrxcx"))).isEqualTo(Judgement.NEAR_MISS)
        }
    }

    @Nested
    inner class 짧은_답은_근접_판정을_하지_않는다 {

        @Test
        fun 한_글자_답에_다른_한_글자를_제출하면_불일치다() {
            // "큐"(길이 1)에 "구" — 편집거리 1이지만 근접으로 오판해선 안 된다.
            val problem = problemWithAnswers("큐", "queue")

            assertThat(problem.judge(AnswerText("구"))).isEqualTo(Judgement.MISMATCH)
        }

        @Test
        fun 두_글자_답에_편집거리_1_오답을_제출하면_불일치다() {
            val problem = problemWithAnswers("스택")

            assertThat(problem.judge(AnswerText("스칵"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 허용답과_충분히_다르면_MISMATCH를_반환한다 {

        @Test
        fun 짧은_답에서_편집거리가_2이면_불일치다() {
            // "cache"(길이 5, 임계 1)에서 두 글자가 바뀐 "caxxe".
            val problem = problemWithAnswers("cache")

            assertThat(problem.judge(AnswerText("caxxe"))).isEqualTo(Judgement.MISMATCH)
        }

        @Test
        fun 길이_7인_답에서_편집거리가_2이면_불일치다() {
            // 길이 7→8 경계의 아래쪽. "process"(길이 7, 임계 1)의 두 글자 교체.
            val problem = problemWithAnswers("process")

            assertThat(problem.judge(AnswerText("prxcexs"))).isEqualTo(Judgement.MISMATCH)
        }

        @Test
        fun 완전히_다른_답은_불일치다() {
            val problem = problemWithAnswers("스택")

            assertThat(problem.judge(AnswerText("큐"))).isEqualTo(Judgement.MISMATCH)
        }
    }

    @Nested
    inner class 생성_시점에_허용답을_검증한다 {

        @Test
        fun 허용답_집합이_비어_있으면_예외를_던진다() {
            assertThatThrownBy { problemWithAnswers() }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("허용답 집합은 비어 있을 수 없습니다.")
        }

        @Test
        fun 허용답에_빈_문자열이_섞여_있으면_예외를_던진다() {
            assertThatThrownBy { problemWithAnswers("스택", " ") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("허용답은 비어 있을 수 없습니다.")
        }
    }

    private fun problemWithAnswers(vararg acceptableAnswers: String): Problem =
        Problem(
            questionText = "질문",
            concept = Concept("개념"),
            acceptableAnswers = acceptableAnswers.toSet(),
            difficulty = Difficulty.EASY,
        )
}
