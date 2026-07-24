package watson.bytecs.problem.domain

import java.text.Normalizer

/**
 * 사용자가 제출한 답변을 표현하는 VO.
 * 생성 시점에 정규화(유니코드 NFC·트림·소문자화·**내부 공백 전부 제거**)하며, 정규화 결과가 비어 있으면 예외를 던진다.
 * 정규화 규칙을 이 한곳에 응집해, 정확 일치와 근접 판정이 동일한 기준으로 비교되도록 보장한다.
 *
 * ⭐️ 띄어쓰기 무시(2026-07-24 오너 결정): 공백을 한 칸으로 축약하지 않고 **전부 제거**한다 — "해시 충돌"·"해시충돌"·"해시  충돌"이
 *    모두 같은 답으로 인정된다. 띄어쓰기가 맞았는지는 CS 지식 정답 여부와 무관하므로 판정 기준에서 뺀다.
 */
@ConsistentCopyVisibility
data class AnswerText private constructor(val value: String) {

    companion object {
        const val BLANK_MESSAGE = "답변은 비어 있을 수 없습니다."
        private val WHITESPACE = Regex("\\s+")

        operator fun invoke(raw: String): AnswerText {
            // NFC를 가장 먼저 적용해, 자모 분해형(NFD)으로 들어온 한글도 조합형과 같게 다룬다.
            // (같은 논리답이 입력 인코딩에 따라 다르게 판정되지 않도록 — 결정성 보장)
            val normalized = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .trim()
                .lowercase()
                .replace(WHITESPACE, "")
            require(normalized.isNotBlank()) { BLANK_MESSAGE }

            return AnswerText(normalized)
        }
    }
}
