package watson.bytecs.review.domain

import kotlin.math.max
import kotlin.math.min

/**
 * 한 번의 정답 통과가 개념 숙련도에 주는 신호. 명세 §3(320~322행)의 '무도움/도움/공개' 3종을 결정적으로 표현한다.
 * 신호는 정답으로 통과한 그 칸(SessionItem)의 상태에서만 파생되며, LLM 호출이 없다(같은 이력 → 같은 신호).
 *
 * [구현 노트 — 오너 튜닝 대상(열린 질문)]
 *  숙련도 레벨(0~4)에 대한 갱신 규칙:
 *   - UNAIDED(무도움): level = min(level+1, 4) — 힌트·정답 공개·오답 교정 힌트 없이 스스로 맞힘(강한 정착).
 *   - AIDED(도움):     level 유지            — 힌트를 열었거나 오답 교정 힌트를 받고 맞힘(약한 정착).
 *   - REVEALED(공개):  level = max(level-1, 0) — 정답 공개(포기)를 쓴 뒤 따라 입력해 맞힘(숙련도↓).
 *  이 사다리 규칙은 MVP 기본값(Leitner 5단계)이며, 실제 학습 효과에 맞춰 오너가 튜닝할 대상이다
 *  (정규화 규칙을 KDoc으로 남긴 기존 관례와 동일 — 공식이 구현 노트라 교체 비용이 낮다).
 */
enum class MasterySignal {
    UNAIDED {
        override fun nextLevel(currentLevel: Int): Int = min(currentLevel + 1, MAX_LEVEL)
    },
    AIDED {
        override fun nextLevel(currentLevel: Int): Int = currentLevel
    },
    REVEALED {
        override fun nextLevel(currentLevel: Int): Int = max(currentLevel - 1, MIN_LEVEL)
    };

    /** 현재 레벨에 이 신호를 적용한 뒤의 레벨(0~4로 절단). */
    abstract fun nextLevel(currentLevel: Int): Int

    companion object {
        const val MIN_LEVEL = 0
        const val MAX_LEVEL = 4

        /**
         * 정답으로 통과한 칸의 도움 신호로부터 숙련도 신호를 판별한다(§3 320~322행).
         *  - 정답 공개를 썼으면 REVEALED(가장 약한 신호가 우선한다 — 포기 후 따라 입력이므로).
         *  - 그 외에 힌트를 하나라도 열었거나 오답 교정 힌트를 받았으면 AIDED.
         *  - 아무 도움도 없었으면 UNAIDED.
         */
        fun of(revealed: Boolean, revealedHintCount: Int, misconceptionHintSeen: Boolean): MasterySignal =
            when {
                revealed -> REVEALED
                revealedHintCount > 0 || misconceptionHintSeen -> AIDED
                else -> UNAIDED
            }
    }
}
