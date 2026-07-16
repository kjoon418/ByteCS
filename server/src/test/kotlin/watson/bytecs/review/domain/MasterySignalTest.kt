package watson.bytecs.review.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * 숙련도 신호의 결정적 판별(§3 320~322행)과 레벨 전이 규칙을 검증한다.
 * 신호 3종(무도움·도움·공개)이 서로 다르게 반영되는지가 수용 기준(349행)의 핵심이다.
 */
class MasterySignalTest {

    @Nested
    inner class 도움_신호를_판별한다 {

        @Test
        fun 아무_도움도_없으면_무도움이다() {
            assertThat(MasterySignal.of(revealed = false, revealedHintCount = 0, misconceptionHintSeen = false))
                .isEqualTo(MasterySignal.UNAIDED)
        }

        @Test
        fun 힌트를_열었으면_도움이다() {
            // 합성 조건(힌트>0 || 교정 힌트) 중 '힌트' 절만 참인 변이 — 교정 힌트는 거짓으로 고정.
            assertThat(MasterySignal.of(revealed = false, revealedHintCount = 1, misconceptionHintSeen = false))
                .isEqualTo(MasterySignal.AIDED)
        }

        @Test
        fun 오답_교정_힌트를_봤으면_도움이다() {
            // 합성 조건 중 '교정 힌트' 절만 참인 변이 — 힌트 수는 0으로 고정. 두 절을 각각 절반씩 검증한다.
            assertThat(MasterySignal.of(revealed = false, revealedHintCount = 0, misconceptionHintSeen = true))
                .isEqualTo(MasterySignal.AIDED)
        }

        @Test
        fun 정답_공개를_썼으면_다른_도움이_없어도_공개다() {
            assertThat(MasterySignal.of(revealed = true, revealedHintCount = 0, misconceptionHintSeen = false))
                .isEqualTo(MasterySignal.REVEALED)
        }

        @Test
        fun 정답_공개는_힌트나_교정_힌트보다_우선한다() {
            assertThat(MasterySignal.of(revealed = true, revealedHintCount = 2, misconceptionHintSeen = true))
                .isEqualTo(MasterySignal.REVEALED)
        }
    }

    @Nested
    inner class 레벨을_전이한다 {

        @Test
        fun 무도움은_레벨을_하나_올리되_최대에서_멈춘다() {
            assertThat(MasterySignal.UNAIDED.nextLevel(2)).isEqualTo(3)
            assertThat(MasterySignal.UNAIDED.nextLevel(MasterySignal.MAX_LEVEL)).isEqualTo(MasterySignal.MAX_LEVEL)
        }

        @Test
        fun 도움은_레벨을_유지한다() {
            assertThat(MasterySignal.AIDED.nextLevel(0)).isEqualTo(0)
            assertThat(MasterySignal.AIDED.nextLevel(3)).isEqualTo(3)
        }

        @Test
        fun 공개는_레벨을_하나_내리되_최소에서_멈춘다() {
            assertThat(MasterySignal.REVEALED.nextLevel(2)).isEqualTo(1)
            assertThat(MasterySignal.REVEALED.nextLevel(MasterySignal.MIN_LEVEL)).isEqualTo(MasterySignal.MIN_LEVEL)
        }
    }
}
