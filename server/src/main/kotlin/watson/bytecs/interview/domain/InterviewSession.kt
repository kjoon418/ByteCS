package watson.bytecs.interview.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate

/**
 * 면접 세션의 애그리거트 루트(계획 §3.3 — C2). 일반 세션(오늘의 한입)과는 별개의 활동으로, 승급된 개념의
 * 면접 질문에 자기 말로 설명을 제출해 AI 루브릭 채점을 받는다. 재제출이 없어([InterviewSessionItem]) 세션은
 * 앞에서부터 순서대로 한 번씩만 지나가며, 마지막 칸까지 답하면(채점 성공이든 폴백이든) 완료된다.
 * 하루에 여러 세션이 있을 수 있다 — '오늘의 면접 세션'은 그 날짜의 가장 최근 세션이다(일반 세션과 같은 관례).
 */
@Entity
@Table(name = "interview_session")
class InterviewSession private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: InterviewSessionStatus = InterviewSessionStatus.IN_PROGRESS
        protected set

    // 낙관적 락(Session.version과 같은 관례) — 동시 제출이 같은 칸을 두 번 전진시키는 것을 막는다.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "interview_session_item",
        joinColumns = [JoinColumn(name = "session_id")],
    )
    @OrderColumn(name = "item_index")
    private val mutableItems: MutableList<InterviewSessionItem> = mutableListOf()

    val items: List<InterviewSessionItem>
        get() = mutableItems.toList()

    val isCompleted: Boolean
        get() = status == InterviewSessionStatus.COMPLETED

    val totalCount: Int
        get() = mutableItems.size

    /** 지금 답해야 할 질문의 위치. 모두 답했으면 items.size다. */
    val currentPosition: Int
        get() = mutableItems.indexOfFirst { !it.isAnswered }.let { if (it == -1) mutableItems.size else it }

    /** 지금 답해야 할 질문의 면접 질문 id. 모두 답했으면 null. */
    fun currentPromptId(): Long? = mutableItems.getOrNull(currentPosition)?.promptId

    /** 지금 답해야 할 질문에서 이미 공개한 힌트 수. 모두 답했으면 0(공개할 현재 질문이 없다, Session 관례 미러). */
    fun currentRevealedHintCount(): Int = mutableItems.getOrNull(currentPosition)?.revealedHintCount ?: 0

    /**
     * 이 세션에 채점 성공(judged=true) 칸이 하나라도 있는지 — 하루 쿼터 차감 여부의 기준이다(계획 §3.3,
     * "카운트 대상 = AI 채점 성공 호출이 포함된 면접 세션의 생성" · "전량 채점 실패면 차감하지 않는다").
     */
    fun hasSuccessfulJudge(): Boolean = mutableItems.any { it.judged }

    /**
     * 현재 질문에 AI 채점 성공 결과를 반영한다. 마지막 칸이었다면 세션을 완료로 전이한다.
     * 이미 완료된 세션에는 더 제출할 수 없다.
     */
    fun recordGraded(submittedText: String, satisfiedPoints: List<Boolean>, comment: String, judgedAt: Instant) {
        val current = currentItemOrThrow()
        current.recordGraded(submittedText, satisfiedPoints, comment, judgedAt)
        completeIfAllAnswered()
    }

    /** 현재 질문에 채점 폴백(실패)을 반영한다. 마지막 칸이었다면 세션을 완료로 전이한다. */
    fun recordFallback(submittedText: String, judgedAt: Instant) {
        val current = currentItemOrThrow()
        current.recordFallback(submittedText, judgedAt)
        completeIfAllAnswered()
    }

    /**
     * 지금 답해야 할 질문의 힌트를 하나 더 공개하고, 갱신된 공개 수를 돌려준다(Session.revealHint와 동일 의미).
     * 더블탭·경쟁 안전: 클라가 아는 현재 공개 수([expectedRevealedCount])가 실제와 일치할 때만 +1 한다.
     * 힌트가 없거나([hintCount]==0) 이미 전부 공개했으면 증가 없이 현재 수를 돌려준다.
     * 힌트 열람은 채점·준비도·쿼터에 영향을 주지 않으므로, 재제출 없는 칸이라도 답하기 전까지는 자유롭게 열 수 있다.
     */
    fun revealHint(expectedRevealedCount: Int, hintCount: Int): Int {
        if (isCompleted) {
            throw InterviewSessionAlreadyCompletedException.forHintReveal()
        }
        val current = mutableItems[currentPosition]
        if (expectedRevealedCount == current.revealedHintCount && current.revealedHintCount < hintCount) {
            current.revealNextHint()
        }
        return current.revealedHintCount
    }

    private fun currentItemOrThrow(): InterviewSessionItem {
        if (isCompleted) {
            throw InterviewSessionAlreadyCompletedException.forAnswer()
        }
        return mutableItems[currentPosition]
    }

    private fun completeIfAllAnswered() {
        if (mutableItems.all { it.isAnswered }) {
            status = InterviewSessionStatus.COMPLETED
        }
    }

    companion object {
        /** 승급 우선순위로 정렬된 면접 질문 id 목록으로 새 세션을 만든다(주어진 순서를 위치 0..n-1로 고정). */
        fun assign(userId: Long, sessionDate: LocalDate, promptIds: List<Long>): InterviewSession {
            require(promptIds.isNotEmpty()) { "면접 세션에 배정할 질문이 있어야 합니다." }

            val session = InterviewSession(userId, sessionDate)
            promptIds.forEachIndexed { index, promptId ->
                session.mutableItems.add(InterviewSessionItem(position = index, promptId = promptId))
            }
            return session
        }
    }
}
