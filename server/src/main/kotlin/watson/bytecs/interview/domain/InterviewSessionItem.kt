package watson.bytecs.interview.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant

/**
 * 면접 세션에 배정된 질문 한 칸. [InterviewSession] 애그리거트에 값으로 소속된다(@Embeddable, Session·SessionItem 관례).
 * 재제출은 없다(1문제 1채점 — 계획 §3.3) — 답을 한 번 기록하면 그 칸은 끝이다.
 * 채점은 성공([recordGraded])하거나 폴백([recordFallback])하며, 성공 여부([judged])가 하루 쿼터 차감의 기준이다.
 */
@Embeddable
class InterviewSessionItem(
    @Column(name = "position", nullable = false)
    val position: Int,

    @Column(name = "prompt_id", nullable = false)
    val promptId: Long,
) {
    init {
        require(position >= 0) { "면접 세션 칸의 위치는 0 이상이어야 합니다. position = $position" }
    }

    @Column(name = "submitted_text", columnDefinition = "text")
    var submittedText: String? = null
        protected set

    // AI 채점이 성공했는지(true) 또는 폴백(실패)했는지(false). 답을 아직 안 냈어도 기본값은 false다.
    @Column(name = "judged", nullable = false)
    var judged: Boolean = false
        protected set

    // 루브릭 포인트 순서에 맞춘 충족 여부 직렬화("1,0,1"). 채점 성공일 때만 채워진다.
    @Column(name = "satisfied_points_raw")
    var satisfiedPointsRaw: String? = null
        protected set

    @Column(name = "judge_comment", columnDefinition = "text")
    var judgeComment: String? = null
        protected set

    @Column(name = "judged_at")
    var judgedAt: Instant? = null
        protected set

    val isAnswered: Boolean
        get() = submittedText != null

    /** 채점 성공 결과가 있으면 포인트별 충족 여부를, 없으면(미답·폴백) 빈 목록을 돌려준다. */
    val satisfiedPoints: List<Boolean>
        get() = satisfiedPointsRaw?.split(",")?.map { it == "1" } ?: emptyList()

    /** AI 채점이 성공했을 때 결과를 기록한다. */
    fun recordGraded(submittedText: String, satisfiedPoints: List<Boolean>, comment: String, judgedAt: Instant) {
        require(!isAnswered) { "이미 답을 제출한 칸입니다(재제출 불가)." }
        this.submittedText = submittedText
        this.judged = true
        this.satisfiedPointsRaw = satisfiedPoints.joinToString(",") { if (it) "1" else "0" }
        this.judgeComment = comment
        this.judgedAt = judgedAt
    }

    /** AI 채점이 실패해 폴백(모범 설명 그대로 공개·준비도 미갱신)했을 때 기록한다. */
    fun recordFallback(submittedText: String, judgedAt: Instant) {
        require(!isAnswered) { "이미 답을 제출한 칸입니다(재제출 불가)." }
        this.submittedText = submittedText
        this.judged = false
        this.judgedAt = judgedAt
    }
}
