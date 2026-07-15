package watson.bytecs.session.application.dto

/**
 * 이미 공개한 힌트 하나. 미공개 힌트 본문은 절대 담기지 않는다(no-leak — 공개분만 싣는다).
 * 클라이언트 HintStepper의 BcsHint(text, codeSnippet)에 그대로 대응한다.
 */
data class RevealedHintResponse(
    val text: String,
    val codeSnippet: String?,
)
