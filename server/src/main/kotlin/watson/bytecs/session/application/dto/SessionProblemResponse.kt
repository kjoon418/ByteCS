package watson.bytecs.session.application.dto

/**
 * 세션에서 '지금 풀 문제'를 표현하는 무낙인·비노출 형태.
 * 개념명·허용답·해설 등 정답을 유추할 수 있는 정보는 절대 포함하지 않는다(문제 슬라이스의 다음 문제 형태와 동일한 계약).
 */
data class SessionProblemResponse(
    val id: Long,
    val question: String,
    val difficulty: String?,
    val codeSnippet: String?,
)
