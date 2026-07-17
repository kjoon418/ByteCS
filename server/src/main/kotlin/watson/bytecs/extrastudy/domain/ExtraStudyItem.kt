package watson.bytecs.extrastudy.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 추가 학습에서 지금 '이어 풀' 한 문제. [ExtraStudy] 애그리거트에 값으로 소속된다(@Embedded, 한 번에 하나).
 * 세션의 [watson.bytecs.session.domain.SessionItem]과 같은 필드 의미를 가지되, 추가 학습은 한 문제만 열어 두므로 리스트가 아니라 단일 값이다.
 *
 * 열린 항목이 없을 때(아직 안 뽑았거나 방금 풀어 비운 상태) 루트의 @Embedded는 null이 된다.
 * JPA가 all-null 컬럼을 null 임베디드로 복원하려면 이 임베디드의 모든 컬럼이 nullable이어야 하므로, 여기선 nullable=false를 두지 않는다.
 * 상태는 [ExtraStudy] 애그리거트가 주도해 바꾸도록, 세터를 protected로 막고 변경 메서드만 노출한다.
 */
@Embeddable
class ExtraStudyItem(
    @Column(name = "open_problem_id")
    val problemId: Long,
) {
    // 정답 공개(모범답안 확인)를 사용했는지. 무낙인이라 벌점이 아니라 도움/포기 신호(기능 3)로만 쓴다.
    @Column(name = "open_revealed")
    var revealed: Boolean = false
        protected set

    // 이 열린 항목에서 누적한 비정답(불일치·근접) 제출 횟수. 복습 신호의 씨앗으로만 쓴다.
    @Column(name = "open_wrong_attempt_count")
    var wrongAttemptCount: Int = 0
        protected set

    // 정답으로 통과할 때 입력한(정규화 전) 답 텍스트. 현재는 보관만 하며 추후 이력 노출에 대비한다.
    @Column(name = "open_submitted_answer", columnDefinition = "text")
    var submittedAnswer: String? = null
        protected set

    // 이 열린 항목에서 공개한 힌트 수(약→강 앞에서부터). 화면 장식이 아니라 학습 기록이다 — 숙련도 반영·재진입 복원에 쓴다.
    @Column(name = "open_revealed_hint_count")
    var revealedHintCount: Int = 0
        protected set

    // 이 열린 항목에서 오답 교정 힌트가 실린 오답을 낸 적이 있는지. 숙련도 산정에서 '무도움'을 판별할 근거다.
    @Column(name = "open_misconception_hint_seen")
    var misconceptionHintSeen: Boolean = false
        protected set

    /** 정답 공개를 사용했음을 기록한다. 공개해도 직접 정답을 입력해야 넘어가므로 진행은 바뀌지 않는다. */
    fun markRevealed() {
        this.revealed = true
    }

    /** 비정답(불일치·근접) 제출을 기록한다. 진행은 전진하지 않는다. */
    fun recordWrongAttempt() {
        this.wrongAttemptCount += 1
    }

    /** 오답 교정 힌트가 실린 오답을 냈음을 기록한다. 무낙인이라 벌점이 아니라 숙련도 신호로만 쓴다. */
    fun markMisconceptionHintSeen() {
        this.misconceptionHintSeen = true
    }

    /** 힌트를 하나 더 공개했음을 기록한다(약→강 앞에서부터 한 칸 전진). 진행은 바뀌지 않는다. */
    fun revealNextHint() {
        this.revealedHintCount += 1
    }
}
