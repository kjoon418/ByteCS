package watson.bytecs.interview.application.dto

/**
 * 면접 힌트 열기(POST /sessions/today/hints/reveal) 응답(HintRevealResponse 관례 미러).
 * hintCount는 현재 질문의 전체 힌트 수, revealedHints는 지금까지 공개한 것만 약→강 순으로 담은 전체 목록이다.
 * 면접 질문 힌트는 텍스트만 다뤄 codeSnippet이 없다. 미공개 힌트 본문은 포함하지 않는다(no-leak).
 */
data class InterviewHintRevealResponse(
    val hintCount: Int,
    val revealedHints: List<String>,
)
