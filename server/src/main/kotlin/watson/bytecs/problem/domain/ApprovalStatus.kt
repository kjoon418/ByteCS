package watson.bytecs.problem.domain

/**
 * 콘텐츠 승인 상태(명세 '콘텐츠 승인 상태' 절). **승인(APPROVED)만 사용자에게 서빙된다.**
 *
 * 명세의 4상태(초안·검수중·승인·반려)에 **회수(RETRACTED)를 상태로 실체화**했다 — 명세는 회수를
 * 행위("승인 해제 → 서빙 중단")로 확정했고, 반려(서빙된 적 없음)와 회수(서빙되다 중단)는 운영상
 * 구별이 필요하다(파이프라인 계획 §4.1, 명세 갱신은 파이프라인 Phase 5).
 *
 * 전이 규칙은 [Problem]의 전이 메서드(startReview·approve·reject·retract)가 강제한다.
 */
enum class ApprovalStatus {
    /** 초안 — 반입·수동 등록 직후. 서빙되지 않는다. */
    DRAFT,

    /** 검수중 — 큐레이터가 검수를 시작한 상태. 서빙되지 않는다. */
    IN_REVIEW,

    /** 승인 — 서빙 가능한 유일한 상태. */
    APPROVED,

    /** 반려 — 검수에서 탈락. 서빙된 적 없다. 수정 후 재검수(startReview)로 되돌릴 수 있다. */
    REJECTED,

    /** 회수 — 서빙되다 승인 해제로 중단. 신규 배정·선정에서 제외된다. 수정 후 재검수로 되돌릴 수 있다. */
    RETRACTED,
}
