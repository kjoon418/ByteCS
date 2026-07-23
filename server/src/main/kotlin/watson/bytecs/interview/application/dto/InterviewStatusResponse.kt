package watson.bytecs.interview.application.dto

/**
 * 면접 세션 홈 카드의 단일 출처(계획 §4.2 GET /api/interview/status).
 *  - candidateConceptCount: 승급 후보 개념 수(레벨≥1 ∧ 승인된 면접 질문 존재). 게스트도 계산된다(가입 유도 문구용).
 *  - remainingQuota: 오늘 남은 세션 수. 게스트는 항상 0(회원 전용이라 진입 자체가 막힌다).
 *  - isGuest: 참이면 클라이언트가 가입 유도 문구를 보여준다.
 */
data class InterviewStatusResponse(
    val candidateConceptCount: Int,
    val remainingQuota: Int,
    val isGuest: Boolean,
)
