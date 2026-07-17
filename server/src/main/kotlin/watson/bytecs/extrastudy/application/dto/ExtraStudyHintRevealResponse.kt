package watson.bytecs.extrastudy.application.dto

/**
 * 추가 학습 힌트 열기(POST /api/extra-study/hints/reveal) 응답. 세션 HintRevealResponse와 동형이다.
 * hintCount는 현재 문제의 전체 힌트 수, revealedHints는 지금까지 공개한 것만 약→강 순으로 담은 전체 목록이다(no-leak).
 */
data class ExtraStudyHintRevealResponse(
    val hintCount: Int,
    val revealedHints: List<ExtraStudyRevealedHintResponse>,
)
