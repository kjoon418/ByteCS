package watson.bytecs.session.application.dto

/**
 * 힌트 열기(POST /today/hints/reveal) 응답.
 * hintCount는 현재 본 문제의 전체 힌트 수, revealedHints는 지금까지 공개한 것만 약→강 순으로 담은 전체 목록이다.
 * 미공개 힌트 본문은 포함하지 않는다(no-leak).
 */
data class HintRevealResponse(
    val hintCount: Int,
    val revealedHints: List<RevealedHintResponse>,
)
