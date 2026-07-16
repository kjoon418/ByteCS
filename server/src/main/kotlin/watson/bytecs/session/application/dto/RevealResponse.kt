package watson.bytecs.session.application.dto

/**
 * 정답 공개(안전판) 응답. 공개 후에도 직접 정답을 입력해야 다음으로 넘어간다.
 * acceptableAnswers는 모범답안 목록(어느 하나를 따라 입력하면 통과), concepts·explanation은 학습 맥락이다(concepts는 태깅 순).
 */
data class RevealResponse(
    val concepts: List<String>,
    val explanation: String?,
    val acceptableAnswers: List<String>,
)
