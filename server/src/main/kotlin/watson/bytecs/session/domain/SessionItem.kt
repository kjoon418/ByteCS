package watson.bytecs.session.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 세션에 배정된 본 문제 한 칸. [Session] 애그리거트에 값으로 소속된다(@Embeddable).
 * 공용 콘텐츠(Problem)와는 식별자(problemId)로만 느슨하게 연결해, 학습 상태(사용자 소유)와 콘텐츠(공용)를 분리한다.
 * 오답 횟수(wrongAttemptCount)는 정답 공개(안전판)를 열 수 있는지의 근거이자, 복습 신호(기능 3)의 씨앗이다.
 * 상태는 [Session] 애그리거트가 주도해서 바꾸도록, 세터를 protected로 막고 변경 메서드만 노출한다.
 */
@Embeddable
class SessionItem(
    @Column(name = "position", nullable = false)
    val position: Int,

    @Column(name = "problem_id", nullable = false)
    val problemId: Long,
) {
    init {
        require(position >= 0) { "세션 칸의 위치는 0 이상이어야 합니다. position = $position" }
    }

    @Column(name = "solved", nullable = false)
    var solved: Boolean = false
        protected set

    // 정답 공개(모범답안 확인)를 사용했는지. 무낙인이라 벌점은 아니고, 안전판 사용 여부·복습 신호로만 쓴다.
    @Column(name = "revealed", nullable = false)
    var revealed: Boolean = false
        protected set

    // 이 칸에서 누적한 비정답(불일치·근접) 제출 횟수. 1 이상이어야 정답 공개(안전판)를 열 수 있다.
    @Column(name = "wrong_attempt_count", nullable = false)
    var wrongAttemptCount: Int = 0
        protected set

    // 정답으로 통과할 때 입력한(정규화된) 정답 텍스트. 지난 문제 다시 보기에서 '내가 쓴 답'으로 노출한다.
    @Column(name = "submitted_answer", columnDefinition = "text")
    var submittedAnswer: String? = null
        protected set

    // 이 칸에서 공개한 힌트 수(약→강 앞에서부터). 화면 장식이 아니라 학습 기록이다 —
    // 숙련도 반영(기능 3)·재진입 복원에 쓴다. `revealed`(정답 공개)와 뜻이 다르니 혼용하지 말 것.
    @Column(name = "revealed_hint_count", nullable = false)
    var revealedHintCount: Int = 0
        protected set

    // 이 칸에서 오답 교정 힌트가 실린 오답을 낸 적이 있는지. 숙련도 산정(기능 3)에서
    // 명세 §3 320행의 '오답 교정 힌트 없이 맞힘'(무도움)을 판별할 근거다 — 교정 힌트를 봤으면 '도움'으로 친다.
    @Column(name = "misconception_hint_seen", nullable = false)
    var misconceptionHintSeen: Boolean = false
        protected set

    /** 정답으로 통과 처리한다. 입력한 정답 텍스트를 남겨 지난 문제 다시 보기에서 재현한다. */
    fun markSolved(submittedAnswer: String) {
        this.solved = true
        this.submittedAnswer = submittedAnswer
    }

    /** 비정답(불일치·근접) 제출을 기록한다. 진행은 전진하지 않고, 정답 공개 가능 여부만 갱신된다. */
    fun recordWrongAttempt() {
        this.wrongAttemptCount += 1
    }

    /** 오답 교정 힌트가 실린 오답을 냈음을 기록한다. 무낙인이라 벌점은 아니고, 숙련도 신호로만 쓴다(진행 불변). */
    fun markMisconceptionHintSeen() {
        this.misconceptionHintSeen = true
    }

    /** 정답 공개를 사용했음을 기록한다. 공개해도 직접 정답을 입력해야 넘어가므로 진행은 바뀌지 않는다. */
    fun markRevealed() {
        this.revealed = true
    }

    /** 힌트를 하나 더 공개했음을 기록한다(약→강 앞에서부터 한 칸 전진). 진행은 바뀌지 않는다. */
    fun revealNextHint() {
        this.revealedHintCount += 1
    }
}
