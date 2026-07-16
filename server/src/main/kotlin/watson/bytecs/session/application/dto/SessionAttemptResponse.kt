package watson.bytecs.session.application.dto

/**
 * 세션 답 제출 결과 응답.
 *  - result: 판정(CORRECT/NEAR_MISS/MISMATCH). 비정답도 200으로 응답한다(무낙인).
 *  - concepts·explanation: 정답(CORRECT)일 때만 채워진다(concepts는 태깅 순). 비정답에서는 no-leak으로 null.
 *  - currentProblem: 정답으로 전진한 뒤 지금 풀 무낙인 문제. 세션이 완료됐으면 null.
 *  - misconceptionHint: 비정답이고 제출이 예상 오답 집합과 정규화 후 일치할 때만 채워진다(그 외 null).
 *    실려도 오답으로 확정되지 않고(무낙인) 정답을 노출하지 않으며, 이때 result는 MISMATCH로 확정된다(근접보다 우선).
 *  - streak: 이 제출로 세션이 완료됐을 때만 갱신된 스트릭을 싣는다(그 외 null).
 */
data class SessionAttemptResponse(
    val result: String,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val concepts: List<String>?,
    val explanation: String?,
    val misconceptionHint: String?,
    val currentProblem: SessionProblemResponse?,
    val streak: StreakResponse?,
)
