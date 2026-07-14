package watson.bytecs.session.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Column
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
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import java.time.LocalDate

/**
 * 일일 학습 세션('오늘의 한입')의 애그리거트 루트.
 * 배정된 본 문제를 순서대로 담고, '첫 미해결 칸'을 커서로 삼아 진행 위치를 도출한다. 하루(userId+날짜)에 하나만 존재한다(유니크 제약).
 * 진행 규칙(정답을 직접 맞혀야만 다음)·정답 공개(안전판)·완료 전이를 모두 이 애그리거트 안에 캡슐화한다.
 * 칸은 값(@Embeddable)으로 소유하므로, 세션과 함께 저장·로딩되며 별도 생명주기를 갖지 않는다.
 */
@Entity
@Table(
    name = "study_session",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_study_session_user_date", columnNames = ["user_id", "session_date"]),
    ],
)
class Session private constructor(
    // 세션은 게스트/회원 구분 없이 userId 기준으로 격리된다.
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    // 하루 경계는 서버 KST 자정. 배정 시점의 KST 날짜를 그대로 보관한다.
    @Column(name = "session_date", nullable = false)
    val sessionDate: LocalDate,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SessionStatus = SessionStatus.IN_PROGRESS
        protected set

    // 낙관적 락. 같은 사용자가 동시에 두 번 제출해도 stale 버전 갱신을 커밋 시점에 막아(커서·오답 횟수 이중 전진 방지),
    // 조용한 상태 손상 대신 낙관적 락 실패(→ 409)로 되돌린다.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "study_session_item",
        joinColumns = [JoinColumn(name = "session_id")],
    )
    @OrderColumn(name = "item_index")
    private val mutableItems: MutableList<SessionItem> = mutableListOf()

    // 방어적 복사로 내부 가변 리스트를 노출하지 않는다(호출자가 캐스팅해 칸을 추가·삭제하며 불변식을 우회하지 못하게).
    val items: List<SessionItem>
        get() = mutableItems.toList()

    /** 지금 풀어야 할 본 문제의 위치. 곧 완료한 본 문제 수와 같고, 모두 마쳤으면 items.size다. */
    val currentPosition: Int
        get() = mutableItems.indexOfFirst { !it.solved }.let { if (it == -1) mutableItems.size else it }

    val isCompleted: Boolean
        get() = status == SessionStatus.COMPLETED

    val solvedCount: Int
        get() = mutableItems.count { it.solved }

    val totalCount: Int
        get() = mutableItems.size

    /** 지금 풀어야 할 본 문제의 식별자. 모두 마쳤으면 null. */
    fun currentItemProblemId(): Long? = mutableItems.getOrNull(currentPosition)?.problemId

    /**
     * 현재 본 문제에 대한 판정 결과를 반영한다. 판정(허용답 대조)은 콘텐츠(Problem.judge)의 책임이므로,
     * 애그리거트는 '이미 산출된 판정'을 현재 칸에 적용하는 역할만 한다.
     *  - CORRECT: 현재 칸을 통과 처리하고, 마지막 칸이었다면 세션을 완료로 전이한다(커서는 자연히 다음으로 이동).
     *  - 그 외(불일치·근접): 오답 횟수만 올리고 전진하지 않는다(무낙인·정답 직접 입력 원칙).
     * 이미 완료된 세션에는 더 제출할 수 없다(추가 연습은 별도 무상태 API 소관).
     */
    fun recordAttempt(judgement: Judgement, answer: AnswerText) {
        if (isCompleted) {
            throw SessionAlreadyCompletedException.forAttempt()
        }
        val current = mutableItems[currentPosition]

        if (judgement == Judgement.CORRECT) {
            current.markSolved(answer.value)
            if (mutableItems.all { it.solved }) {
                status = SessionStatus.COMPLETED
            }
        } else {
            current.recordWrongAttempt()
        }
    }

    /**
     * 현재 본 문제의 정답 공개(안전판)를 기록한다.
     * 안전판은 한 번이라도 비정답을 제출한 뒤에만 열 수 있다(정답을 곧바로 흘리지 않기 위한 게이트).
     * 공개해도 직접 정답을 입력해야 넘어가므로 진행은 바꾸지 않는다.
     */
    fun reveal() {
        if (isCompleted) {
            throw SessionAlreadyCompletedException.forReveal()
        }
        val current = mutableItems[currentPosition]
        if (current.wrongAttemptCount < MIN_WRONG_ATTEMPTS_TO_REVEAL) {
            throw RevealNotAllowedException.beforeAnyWrongAttempt()
        }
        current.markRevealed()
    }

    /**
     * 이미 지나온(통과한) 본 문제를 읽기 전용으로 돌려준다. 아직 도달하지 않은 칸은 노출하지 않는다(no-leak).
     * 조회는 진행·완료 카운트에 영향을 주지 않는다.
     * currentPosition 이상의 위치는(범위를 벗어난 위치 포함) 모두 ItemNotViewableException으로 막는다 —
     * '없음(404)'과 '아직 못 봄(403)'을 구분하지 않는 것은 의도된 no-leak이다(남은 칸 수를 흘리지 않는다).
     */
    fun pastItemAt(position: Int): SessionItem {
        if (position < 0 || position >= currentPosition) {
            throw ItemNotViewableException.at(position)
        }
        return mutableItems[position]
    }

    companion object {
        // 정답 공개(안전판)를 열 수 있는 최소 비정답 제출 횟수.
        private const val MIN_WRONG_ATTEMPTS_TO_REVEAL = 1

        /**
         * 배정된 본 문제 식별자 목록으로 새 세션을 만든다(주어진 순서를 그대로 위치 0..n-1로 고정).
         * 빈 배정은 '오늘의 한입'이 성립하지 않으므로 허용하지 않는다(서비스가 폴백으로 항상 비지 않게 보장).
         */
        fun assign(userId: Long, sessionDate: LocalDate, problemIds: List<Long>): Session {
            require(problemIds.isNotEmpty()) { "세션에 배정할 본 문제가 있어야 합니다." }

            val session = Session(userId, sessionDate)
            problemIds.forEachIndexed { index, problemId ->
                session.mutableItems.add(SessionItem(position = index, problemId = problemId))
            }
            return session
        }
    }
}
