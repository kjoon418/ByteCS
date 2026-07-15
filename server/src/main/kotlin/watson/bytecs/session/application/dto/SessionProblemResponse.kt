package watson.bytecs.session.application.dto

/**
 * 세션에서 '지금 풀 문제'를 표현하는 무낙인·비노출 형태.
 * 개념명·허용답·해설 등 정답을 유추할 수 있는 정보는 절대 포함하지 않는다(문제 슬라이스의 다음 문제 형태와 동일한 계약).
 *
 * 힌트는 개수(hintCount)만 항상 싣고, 본문은 이미 공개한 것(revealedHints)만 싣는다 — 미공개 힌트 본문은 no-leak.
 * hintCount가 0이면 클라이언트는 힌트 진입점 자체를 노출하지 않는다.
 * revealedHints는 재진입 복원용이라 약→강 순으로 이미 연 것을 담는다(갓 전진한 새 문제라면 빈 목록).
 */
data class SessionProblemResponse(
    val id: Long,
    val question: String,
    val difficulty: String?,
    val codeSnippet: String?,
    val hintCount: Int,
    val revealedHints: List<RevealedHintResponse>,
)
