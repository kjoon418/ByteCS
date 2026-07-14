package watson.bytecs.session.application.dto

/**
 * 지난 문제 다시 보기(읽기 전용) 응답. 이미 통과한 칸이므로 개념·모범답안을 공개해도 학습 효과를 해치지 않는다.
 * result는 통과 판정(항상 CORRECT), submittedAnswer는 내가 입력한 정답, revealed는 그때 정답 공개를 썼는지다.
 */
data class PastItemResponse(
    val position: Int,
    val problemId: Long,
    val question: String,
    val codeSnippet: String?,
    val difficulty: String?,
    val submittedAnswer: String?,
    val result: String,
    val revealed: Boolean,
    val concept: String,
    val explanation: String?,
    val acceptableAnswers: List<String>,
)
