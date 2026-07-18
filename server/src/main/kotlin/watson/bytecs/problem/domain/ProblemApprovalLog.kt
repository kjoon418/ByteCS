package watson.bytecs.problem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 문제 승인 상태 전이의 감사 기록(누가·언제·어느 상태에서 어느 상태로·사유).
 * 반려·회수 사유의 재확인과, 큐레이터가 늘었을 때의 감사 추적이 목적이다(파이프라인 계획 §4.3).
 * 기록 축적은 관리자 검수 흐름(파이프라인 Phase 2)이 담당한다 — 전이 자체는 [Problem]의 전이 메서드가 수행하고,
 * 이 엔티티는 그 결과를 부수 기록으로 남긴다(로그이므로 [Problem] 애그리거트에 연관을 두지 않고 id로만 참조).
 */
@Entity
@Table(name = "problem_approval_log")
class ProblemApprovalLog(
    @Column(name = "problem_id", nullable = false)
    val problemId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    val fromStatus: ApprovalStatus,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    val toStatus: ApprovalStatus,

    /** 반려·회수 사유 등 큐레이터가 남기는 자유 서술(선택). */
    @Column(name = "reason", columnDefinition = "text")
    val reason: String? = null,

    /** 전이를 수행한 관리자 사용자 id. */
    @Column(name = "actor_user_id", nullable = false)
    val actorUserId: Long,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
