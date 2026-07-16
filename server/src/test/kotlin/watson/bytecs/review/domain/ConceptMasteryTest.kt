package watson.bytecs.review.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 개념 숙련도의 결정적 갱신을 검증한다(§3 수용 기준 348행 — 같은 이력 → 같은 숙련도·시점).
 * 간격 사다리 [1,3,7,14,30]과 신호별 레벨 전이가 다음 복습 시점을 어떻게 미는지가 핵심이다.
 */
class ConceptMasteryTest {

    private companion object {
        const val USER_ID = 1L
        const val CONCEPT_ID = 10L
        const val PROBLEM_ID = 100L
        val SOLVED_ON: LocalDate = LocalDate.of(2026, 7, 14)
    }

    @Nested
    inner class 신규_개념을_첫_통과로_만든다 {

        @Test
        fun 무도움_첫_통과는_레벨_1과_3일_뒤_복습이_된다() {
            val mastery = firstSolve(MasterySignal.UNAIDED)

            assertThat(mastery.level).isEqualTo(1)
            // 사다리[1] = 3일.
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(3))
            assertThat(mastery.lastProblemId).isEqualTo(PROBLEM_ID)
            assertThat(mastery.userId).isEqualTo(USER_ID)
            assertThat(mastery.conceptId).isEqualTo(CONCEPT_ID)
        }

        @Test
        fun 도움_첫_통과는_레벨_0과_1일_뒤_복습이_된다() {
            val mastery = firstSolve(MasterySignal.AIDED)

            assertThat(mastery.level).isEqualTo(0)
            // 사다리[0] = 1일.
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(1))
        }

        @Test
        fun 공개_첫_통과는_레벨_0과_1일_뒤_복습이_된다() {
            val mastery = firstSolve(MasterySignal.REVEALED)

            assertThat(mastery.level).isEqualTo(0)
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(1))
        }
    }

    @Nested
    inner class 통과를_거듭하며_간격을_민다 {

        @Test
        fun 무도움을_거듭하면_레벨과_간격이_사다리를_따라_커진다() {
            val mastery = firstSolve(MasterySignal.UNAIDED) // level 1

            mastery.applySolve(MasterySignal.UNAIDED, SOLVED_ON.plusDays(3), PROBLEM_ID) // level 2 → 7일
            assertThat(mastery.level).isEqualTo(2)
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(3).plusDays(7))

            mastery.applySolve(MasterySignal.UNAIDED, SOLVED_ON.plusDays(10), PROBLEM_ID) // level 3 → 14일
            assertThat(mastery.level).isEqualTo(3)
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(10).plusDays(14))
        }

        @Test
        fun 레벨은_최대_4에서_멈추고_간격은_30일로_고정된다() {
            val mastery = firstSolve(MasterySignal.UNAIDED)
            repeat(10) { mastery.applySolve(MasterySignal.UNAIDED, SOLVED_ON, PROBLEM_ID) }

            assertThat(mastery.level).isEqualTo(4)
            // 사다리[4] = 30일.
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(30))
        }

        @Test
        fun 공개는_레벨을_내려_간격을_좁힌다() {
            val mastery = firstSolve(MasterySignal.UNAIDED) // level 1
            mastery.applySolve(MasterySignal.UNAIDED, SOLVED_ON, PROBLEM_ID) // level 2

            mastery.applySolve(MasterySignal.REVEALED, SOLVED_ON, PROBLEM_ID) // level 1 → 3일

            assertThat(mastery.level).isEqualTo(1)
            assertThat(mastery.nextReviewDate).isEqualTo(SOLVED_ON.plusDays(3))
        }

        @Test
        fun 갱신한_문제를_그때_푼_그_문제로_남긴다() {
            val mastery = firstSolve(MasterySignal.UNAIDED)

            val laterProblemId = 200L
            mastery.applySolve(MasterySignal.AIDED, SOLVED_ON.plusDays(3), laterProblemId)

            assertThat(mastery.lastProblemId).isEqualTo(laterProblemId)
        }
    }

    private fun firstSolve(signal: MasterySignal): ConceptMastery =
        ConceptMastery.firstSolve(USER_ID, CONCEPT_ID, signal, SOLVED_ON, PROBLEM_ID)
}
