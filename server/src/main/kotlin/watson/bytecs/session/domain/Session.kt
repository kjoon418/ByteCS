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

    /** 지금 풀어야 할 본 문제에서 이미 공개한 힌트 수. 모두 마쳤으면 0(공개할 현재 문제가 없다). */
    fun currentRevealedHintCount(): Int = mutableItems.getOrNull(currentPosition)?.revealedHintCount ?: 0

    /**
     * 현재 본 문제에 대한 판정 결과를 반영한다. 판정(허용답 대조)은 콘텐츠(Problem.judge)의 책임이므로,
     * 애그리거트는 '이미 산출된 판정'을 현재 칸에 적용하는 역할만 한다.
     *  - CORRECT: 현재 칸을 통과 처리하고, 마지막 칸이었다면 세션을 완료로 전이한다(커서는 자연히 다음으로 이동).
     *  - 그 외(불일치·근접): 오답 횟수만 올리고 전진하지 않는다(무낙인·정답 직접 입력 원칙).
     * [misconceptionShown]이 참이면(이 비정답에 오답 교정 힌트가 실렸으면) 그 사실을 칸에 마킹한다 —
     * 숙련도 산정(기능 3)이 '오답 교정 힌트 없이 맞힘'(무도움)을 판별할 근거다.
     * 이미 완료된 세션에는 더 제출할 수 없다(추가 학습은 별도 무상태 API 소관).
     */
    fun recordAttempt(judgement: Judgement, answer: AnswerText, misconceptionShown: Boolean = false) {
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
            if (misconceptionShown) {
                current.markMisconceptionHintSeen()
            }
        }
    }

    /**
     * 현재 본 문제의 정답 공개(안전판)를 기록한다.
     * [결정 2026-07-17] 사용자가 원하면 시도 전에도 열 수 있다(선행 오답 요구 없음, 무낙인). 공개는 도움/포기
     * 신호로 기록되므로(기능 3) 시도 전 공개도 동일하게 숙련도에 반영된다.
     * 공개해도 직접 정답을 입력해야 넘어가므로 진행은 바꾸지 않는다. 이미 완료된 세션에는 공개할 수 없다.
     */
    fun reveal() {
        if (isCompleted) {
            throw SessionAlreadyCompletedException.forReveal()
        }
        mutableItems[currentPosition].markRevealed()
    }

    /**
     * 현재 본 문제의 힌트를 하나 더 공개하고, 갱신된 공개 수를 돌려준다.
     * 더블탭·경쟁 안전: 클라가 아는 현재 공개 수([expectedRevealedCount])가 실제와 일치할 때만 +1 한다.
     * 힌트가 없거나([hintCount]==0) 이미 전부 공개했으면 증가 없이 현재 수를 돌려준다.
     * 힌트 공개는 정답 공개(안전판)와 달리 선행 오답을 요구하지 않는다 — 막힘의 순간에 바로 필요하다.
     */
    fun revealHint(expectedRevealedCount: Int, hintCount: Int): Int {
        if (isCompleted) {
            throw SessionAlreadyCompletedException.forHintReveal()
        }
        val current = mutableItems[currentPosition]
        if (expectedRevealedCount == current.revealedHintCount && current.revealedHintCount < hintCount) {
            current.revealNextHint()
        }
        return current.revealedHintCount
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
