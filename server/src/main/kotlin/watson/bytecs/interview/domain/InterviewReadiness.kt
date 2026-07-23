package watson.bytecs.interview.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * 면접 준비도(사용자·개념별 설명 검증 상태, 계획 §3.3). [watson.bytecs.review.domain.ConceptMastery]와 독립된 축이다(DI4) —
 * AI 채점 성공 결과만 여기에 반영되고, 숙련도 레벨·간격 사다리는 절대 바꾸지 않는다.
 * 채점 폴백(실패)은 이 상태를 갱신하지 않는다(호출부가 폴백일 때 [applyResult]를 호출하지 않는다).
 */
@Entity
@Table(
    name = "interview_readiness",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_interview_readiness", columnNames = ["user_id", "concept_id"]),
    ],
)
class InterviewReadiness private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "concept_id", nullable = false)
    val conceptId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: InterviewReadinessStatus = InterviewReadinessStatus.UNVERIFIED
        protected set

    @Column(name = "satisfied_count", nullable = false)
    var satisfiedCount: Int = 0
        protected set

    @Column(name = "total_count", nullable = false)
    var totalCount: Int = 0
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: Instant
        protected set

    /** '검증됨' 미달(부분·미검증)인지 — 면접 세션 결과 화면의 역방향 연결(DI10)·복습 당김(DI11) 조건이다. */
    val isBelowVerified: Boolean
        get() = status != InterviewReadinessStatus.VERIFIED

    /**
     * 채점 성공 결과를 반영해 상태를 파생한다: 전 포인트 충족 → VERIFIED, 일부 충족 → PARTIAL, 0개 충족 → UNVERIFIED.
     * 재도전을 허용하므로([계획 §3.3]) 이전 결과를 덮어쓴다(그때그때 최신 결과가 준비도다).
     */
    fun applyResult(satisfiedCount: Int, totalCount: Int, updatedAt: Instant) {
        require(totalCount > 0) { "루브릭 포인트 총 개수는 1 이상이어야 합니다." }
        require(satisfiedCount in 0..totalCount) { "충족 포인트 수는 0..totalCount 범위여야 합니다." }

        this.satisfiedCount = satisfiedCount
        this.totalCount = totalCount
        this.status = when {
            satisfiedCount == totalCount -> InterviewReadinessStatus.VERIFIED
            satisfiedCount > 0 -> InterviewReadinessStatus.PARTIAL
            else -> InterviewReadinessStatus.UNVERIFIED
        }
        this.updatedAt = updatedAt
    }

    companion object {
        /** 아직 채점 결과가 없는 초기 준비도(신규 행). */
        fun initial(userId: Long, conceptId: Long): InterviewReadiness =
            InterviewReadiness(userId, conceptId)
    }
}
