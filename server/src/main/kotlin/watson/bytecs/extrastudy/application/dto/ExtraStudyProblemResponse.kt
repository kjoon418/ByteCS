package watson.bytecs.extrastudy.application.dto

/**
 * 추가 학습에서 '지금 풀 문제'를 표현하는 무낙인·비노출 형태(세션 SessionProblemResponse와 동일 필드).
 * 개념명·허용답·해설 등 정답을 유추할 수 있는 정보는 절대 포함하지 않는다(no-leak).
 *
 * 힌트는 개수(hintCount)만 항상 싣고, 본문은 이미 공개한 것(revealedHints)만 약→강 순으로 싣는다 — 미공개 힌트 본문은 no-leak.
 * revealedHints는 재진입 복원용이라, 이 열린 항목에서 이미 연 만큼 담는다(갓 뽑은 새 문제라면 빈 목록).
 */
data class ExtraStudyProblemResponse(
    val id: Long,
    val question: String,
    val difficulty: String?,
    val codeSnippet: String?,
    val hintCount: Int,
    val revealedHints: List<ExtraStudyRevealedHintResponse>,
)
