package watson.bytecs.problem.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [Problem.evaluate] — 판정과 오답 교정 힌트를 함께 산출한다.
 * 특히 '오답 교정 힌트 매칭이 근접(NEAR_MISS)보다 우선한다'는 합성 규칙을 절반씩 따로 죽여 고정한다(인수인계 §5.2).
 */
class ProblemEvaluateTest {

    @Nested
    inner class 오답_교정_힌트를_함께_산출한다 {

        @Test
        fun 비정답이_예상_오답에_매칭되면_MISMATCH로_확정하고_교정_힌트를_싣는다() {
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                misconceptions = listOf(misconception(setOf("프로세스"), "프로세스는 프로그램 자체예요.")),
            )

            val outcome = problem.evaluate(AnswerText("프로세스"))

            assertThat(outcome.judgement).isEqualTo(Judgement.MISMATCH)
            assertThat(outcome.misconceptionHint).isEqualTo("프로세스는 프로그램 자체예요.")
        }

        @Test
        fun 예상_오답에_매칭되지_않는_비정답은_교정_힌트_없이_판정_그대로다() {
            // 합성 규칙의 '매칭' 절 검증: 매칭이 없으면 교정 힌트를 실어선 안 된다.
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                misconceptions = listOf(misconception(setOf("프로세스"), "프로세스는 프로그램 자체예요.")),
            )

            val outcome = problem.evaluate(AnswerText("커널"))

            assertThat(outcome.judgement).isEqualTo(Judgement.MISMATCH)
            assertThat(outcome.misconceptionHint).isNull()
        }

        @Test
        fun 정답이면_예상_오답_집합에_들어_있어도_교정_힌트를_싣지_않는다() {
            // 합성 규칙의 '비정답' 절 검증: 정답이면 교정 힌트 계산 자체를 건너뛴다.
            // (교정 힌트 집합에 정답 표기를 일부러 넣어, 정답 조기 반환이 없으면 힌트가 새는지 확인한다)
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                misconceptions = listOf(misconception(setOf("스레드"), "이 메시지가 새면 안 된다.")),
            )

            val outcome = problem.evaluate(AnswerText("스레드"))

            assertThat(outcome.judgement).isEqualTo(Judgement.CORRECT)
            assertThat(outcome.misconceptionHint).isNull()
        }
    }

    @Nested
    inner class 교정_힌트_매칭은_근접보다_우선한다 {

        @Test
        fun 근접_수준으로_가까운_답이라도_예상_오답에_매칭되면_MISMATCH로_확정된다() {
            // "스레드"(길이 3, 정의 재생형)에 "스레두" — judge()만 보면 편집거리 1이라 NEAR_MISS다.
            // 그러나 예상 오답으로 큐레이션돼 있으므로, 그것은 '다른 개념의 답'이지 오타가 아니다 → MISMATCH로 확정.
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                type = ProblemType.DEFINITION_RECALL,
                misconceptions = listOf(misconception(setOf("스레두"), "그건 다른 답이에요.")),
            )

            // 전제 고정: 교정 힌트가 없었다면 이 답은 근접이다.
            assertThat(problem.judge(AnswerText("스레두"))).isEqualTo(Judgement.NEAR_MISS)

            val outcome = problem.evaluate(AnswerText("스레두"))

            assertThat(outcome.judgement).isEqualTo(Judgement.MISMATCH)
            assertThat(outcome.misconceptionHint).isEqualTo("그건 다른 답이에요.")
        }

        @Test
        fun 예상_오답에_매칭되지_않는_근접_답은_NEAR_MISS로_남는다() {
            // 같은 문제라도 예상 오답에 없는 오타는 근접 신호를 그대로 유지한다(우선 규칙이 근접을 통째로 죽이지 않는다).
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                type = ProblemType.DEFINITION_RECALL,
                misconceptions = listOf(misconception(setOf("스레두"), "그건 다른 답이에요.")),
            )

            val outcome = problem.evaluate(AnswerText("스레들"))

            assertThat(outcome.judgement).isEqualTo(Judgement.NEAR_MISS)
            assertThat(outcome.misconceptionHint).isNull()
        }
    }

    @Nested
    inner class 예상_오답_매칭은_허용답과_같은_정규화를_쓴다 {

        @Test
        fun 대소문자와_공백이_달라도_정규화_후_일치하면_매칭된다() {
            val problem = problemWith(
                acceptableAnswers = setOf("스레드"),
                misconceptions = listOf(misconception(setOf("Process"), "프로그램 자체예요.")),
            )

            val outcome = problem.evaluate(AnswerText("  process "))

            assertThat(outcome.misconceptionHint).isEqualTo("프로그램 자체예요.")
        }
    }

    private fun problemWith(
        acceptableAnswers: Set<String>,
        type: ProblemType? = ProblemType.DEFINITION_RECALL,
        misconceptions: List<MisconceptionHint> = emptyList(),
    ): Problem =
        Problem(
            questionText = "질문",
            concepts = listOf(Concept("개념")),
            acceptableAnswers = acceptableAnswers,
            // 평가 테스트는 대표 정답을 관찰하지 않으므로, 불변식을 만족하도록 첫 허용답으로 채운다.
            representativeAnswer = acceptableAnswers.first(),
            type = type,
            difficulty = Difficulty.EASY,
            misconceptionHints = misconceptions,
        )

    private fun misconception(expected: Set<String>, message: String): MisconceptionHint =
        MisconceptionHint(expectedAnswers = expected, message = message)
}
