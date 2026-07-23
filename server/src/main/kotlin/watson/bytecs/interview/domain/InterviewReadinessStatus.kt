package watson.bytecs.interview.domain

/**
 * 면접 준비도(개념별 설명 검증 상태, 계획 §3.3). 기존 숙련도([watson.bytecs.review.domain.ConceptMastery])와
 * 독립된 축이다 — AI 채점 결과는 여기에만 반영되고 숙련도 레벨에는 반영되지 않는다(DI4).
 */
enum class InterviewReadinessStatus {
    UNVERIFIED,
    PARTIAL,
    VERIFIED,
}
