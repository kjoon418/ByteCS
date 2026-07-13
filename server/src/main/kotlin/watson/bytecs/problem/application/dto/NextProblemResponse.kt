package watson.bytecs.problem.application.dto

/**
 * 다음 문제 응답.
 * 개념명·허용답 등 정답을 유추할 수 있는 정보는 절대 포함하지 않는다.
 */
data class NextProblemResponse(
    val id: Long,
    val question: String,
    val difficulty: String?,
    val codeSnippet: String?,
)
