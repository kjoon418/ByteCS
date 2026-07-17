package watson.bytecs.extrastudy.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import watson.bytecs.problem.domain.Judgement

/**
 * 추가 학습(Extra Study)의 애그리거트 루트. 사용자당 한 행만 존재한다(유니크 제약).
 *
 * 세션과 달리 목표 분량·완결(완료 카운트·스트릭)이 없고, **한 번에 한 문제**만 열어 둔다([openItem]).
 * 이탈 후 재진입 시 그 문제를 이어 풀며, 새로고침으로 다른/쉬운 문제를 다시 뽑는 악용을 막는다([assignOpen]이 이미 열려 있으면 갈아끼우지 않음).
 * 정답으로 통과한 문제는 [solvedProblemIds]로 승격되어 이후 선정(세션·추가 학습 공통)에서 '이미 푼 문제'로 제외된다.
 *
 * 학습 데이터 의미론은 세션과 동일하다 — 숙련도·복습 갱신은 서비스가 정답 통과 시 [watson.bytecs.review.application.ReviewService]로 처리한다.
 */
@Entity
@Table(
    name = "extra_study",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_extra_study_user", columnNames = ["user_id"]),
    ],
)
class ExtraStudy private constructor(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    // 낙관적 락. 같은 사용자가 동시에 두 번 제출·공개해도 stale 버전 갱신을 커밋 시점에 막아 이중 승격을 방지한다.
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    // 지금 이어 풀 열린 항목(미해결) 1개. 없으면 null(아직 안 뽑았거나 방금 풀어 비운 상태).
    @Embedded
    var openItem: ExtraStudyItem? = null
        protected set

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "extra_study_solved",
        joinColumns = [JoinColumn(name = "extra_study_id")],
    )
    @Column(name = "problem_id", nullable = false)
    private val mutableSolvedProblemIds: MutableSet<Long> = mutableSetOf()

    // 방어적 복사로 내부 가변 집합을 노출하지 않는다(호출자가 캐스팅해 불변식을 우회하지 못하게).
    val solvedProblemIds: Set<Long>
        get() = mutableSolvedProblemIds.toSet()

    /** 지금 이어 풀 문제의 식별자. 열린 항목이 없으면 null. */
    fun openProblemId(): Long? = openItem?.problemId

    /**
     * 열린 항목이 없을 때만 새 문제를 연다. 이미 열려 있으면 갈아끼우지 않는다 —
     * 새로고침으로 다른/쉬운 문제를 다시 뽑는 악용을 차단한다(재진입 이어 풀기).
     */
    fun assignOpen(problemId: Long) {
        if (openItem == null) {
            openItem = ExtraStudyItem(problemId)
        }
    }

    /**
     * 현재 열린 항목에 판정 결과를 반영한다.
     *  - CORRECT: 그 문제를 solved로 승격하고 열린 항목을 비운다(다음 조회에서 새 문제를 뽑는다).
     *  - 그 외(불일치·근접): 오답 횟수만 올리고([misconceptionShown]이면 교정 힌트 봄도 마킹) 열린 항목을 유지한다(무낙인·정답 직접 입력 원칙).
     * 열린 항목이 없으면 [ExtraStudyNoOpenItemException](경합).
     */
    fun recordAttempt(judgement: Judgement, submittedAnswer: String, misconceptionShown: Boolean = false) {
        val open = openItem ?: throw ExtraStudyNoOpenItemException.forAttempt()
        if (judgement == Judgement.CORRECT) {
            mutableSolvedProblemIds.add(open.problemId)
            openItem = null
        } else {
            open.recordWrongAttempt()
            if (misconceptionShown) {
                open.markMisconceptionHintSeen()
            }
        }
    }

    /**
     * 현재 열린 항목의 정답 공개(안전판)를 기록한다. 시도 전에도 허용된다([결정 2026-07-17]).
     * 공개해도 직접 정답을 입력해야 넘어가므로 진행은 바뀌지 않는다. 열린 항목이 없으면 예외.
     */
    fun reveal() {
        val open = openItem ?: throw ExtraStudyNoOpenItemException.forReveal()
        open.markRevealed()
    }

    /**
     * 현재 열린 항목의 힌트를 하나 더 공개하고, 갱신된 공개 수를 돌려준다.
     * 더블탭·경쟁 안전: 클라가 아는 공개 수([expectedRevealedCount])가 실제와 일치할 때만 +1 한다.
     * 힌트가 없거나 이미 전부 공개했으면 증가 없이 현재 수를 돌려준다. 열린 항목이 없으면 예외.
     */
    fun revealHint(expectedRevealedCount: Int, hintCount: Int): Int {
        val open = openItem ?: throw ExtraStudyNoOpenItemException.forHintReveal()
        if (expectedRevealedCount == open.revealedHintCount && open.revealedHintCount < hintCount) {
            open.revealNextHint()
        }
        return open.revealedHintCount
    }

    companion object {
        /** 아직 아무 문제도 열지 않은 빈 추가 학습을 만든다(사용자당 1행). */
        fun create(userId: Long): ExtraStudy = ExtraStudy(userId)
    }
}
