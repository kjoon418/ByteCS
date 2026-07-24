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
import jakarta.persistence.ManyToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table
import watson.bytecs.problem.domain.ApprovalStatus
import watson.bytecs.problem.domain.Concept
import watson.bytecs.problem.domain.InvalidApprovalStateException

/**
 * 면접 질문(Interview Prompt) — 개념에 귀속되는 큐레이션 콘텐츠(계획 §3.3, 명세 기능 8).
 * 질문 문구 + 모범 설명 + **루브릭**(꼭 짚어야 할 핵심 포인트 목록, 순서 보존)으로 이뤄진다.
 *
 * 승인 게이트 대상이다 — 문제와 **같은 상태 모델([ApprovalStatus])·전이 규칙**을 재사용하며, 승인(APPROVED)만 서빙된다.
 * 전이는 아래 전이 메서드로만 일어나고, 허용되지 않는 전이는 [InvalidApprovalStateException]으로 거부된다(Problem 관례와 동일).
 * 개념 1 — 0..N 면접 질문(스키마는 N을 허용한다. 1차 운영은 개념당 1개를 기본으로 하되, 도메인이 강제하지는 않는다).
 *
 * 공용 콘텐츠(개념 소유)라 학습 상태와 달리 [Concept]를 직접 참조한다(Problem이 개념을 참조하는 것과 같은 결).
 */
@Entity
@Table(name = "interview_prompt")
class InterviewPrompt(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concept_id", nullable = false)
    val concept: Concept,

    @Column(name = "question", nullable = false, columnDefinition = "text")
    val question: String,

    @Column(name = "model_answer", nullable = false, columnDefinition = "text")
    val modelAnswer: String,

    /**
     * 루브릭 핵심 포인트(하나 이상). AI 채점이 "사용자의 설명이 각 포인트를 짚었는가"를 대조하는 기준이다(계획 §3.3).
     * 순서는 소유 리스트의 인덱스([OrderColumn])로 보존한다 — 채점·결과 표시에서 포인트 순서가 결정적이어야 한다.
     */
    @ElementCollection
    @CollectionTable(
        name = "interview_prompt_rubric_point",
        joinColumns = [JoinColumn(name = "prompt_id")],
    )
    @OrderColumn(name = "position")
    @Column(name = "point_text", nullable = false, columnDefinition = "text")
    val rubricPoints: List<String>,

    /**
     * 약→강 순서의 점진 공개 힌트(0~N개, 선택 — Problem.hints 관례 미러). 힌트 열람은 채점·준비도·쿼터에
     * 어떤 영향도 주지 않는다(무낙인). 면접 질문은 코드 스니펫 개념이 없어 텍스트만 다룬다.
     * 순서는 소유 리스트의 인덱스([OrderColumn])로 보존하며, 미공개 힌트 본문이 새어 나가지 않도록
     * 응답에는 [revealedHints]로 공개분만 잘라 싣는다(no-leak). 선택 필드라 기존 DB 행·기존 시드와 하위 호환된다.
     * 아래 [hints] 프로퍼티(클래스 본문)로 옮겨 [updateHints]가 재할당할 수 있게 한다(생성자 프로퍼티는 setter 접근 제어자를 못 붙인다 — approvalStatus 관례와 동일).
     */
    hints: List<String> = emptyList(),

    /**
     * 생성 시점의 승인 상태. 신규 등록(반입·수동)의 기본은 초안이다.
     * 시드 로더만 예외적으로 APPROVED로 생성한다(MVP는 시딩분을 승인 취급 — Problem 시드 관례와 동일).
     */
    approvalStatus: ApprovalStatus = ApprovalStatus.DRAFT,
) {
    init {
        require(question.isNotBlank()) { "면접 질문 문구는 비어 있을 수 없습니다." }
        require(modelAnswer.isNotBlank()) { "면접 질문의 모범 설명은 비어 있을 수 없습니다." }
        require(rubricPoints.isNotEmpty()) { "루브릭은 핵심 포인트를 하나 이상 가져야 합니다." }
        require(rubricPoints.none { it.isBlank() }) { "루브릭 핵심 포인트는 비어 있을 수 없습니다." }
        require(hints.none { it.isBlank() }) { "면접 질문 힌트 항목은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    var approvalStatus: ApprovalStatus = approvalStatus
        protected set

    @ElementCollection
    @CollectionTable(
        name = "interview_prompt_hint",
        joinColumns = [JoinColumn(name = "prompt_id")],
    )
    @OrderColumn(name = "hint_index")
    @Column(name = "hint_text", nullable = false, columnDefinition = "text")
    var hints: List<String> = hints
        protected set

    /** 검수를 시작한다. 초안·반려·회수 상태에서만 가능하다(반려·회수는 수정 후 재검수 경로). */
    fun startReview() {
        validateTransition(
            approvalStatus in REVIEWABLE_STATUSES,
            "검수는 초안·반려·회수 상태에서만 시작할 수 있습니다. 현재 상태 = $approvalStatus",
        )
        approvalStatus = ApprovalStatus.IN_REVIEW
    }

    /**
     * 승인한다. 검수중에서만 가능하며, 결정적 구조 검증(루브릭 1개 이상·모범 설명 존재·개념 귀속)을 통과해야 한다.
     * (질문·모범 설명 비어있지 않음·루브릭 1개 이상·개념 귀속은 생성자 init·NOT NULL이 이미 보장하지만,
     *  시드처럼 승인 상태로 곧장 생성하는 경로가 같은 구조 요건을 통과했는지 [validateApprovable]로 재확인한다 — Problem 관례.)
     */
    fun approve() {
        validateTransition(
            approvalStatus == ApprovalStatus.IN_REVIEW,
            "승인은 검수중 상태에서만 할 수 있습니다. 현재 상태 = $approvalStatus",
        )
        validateApprovable()
        approvalStatus = ApprovalStatus.APPROVED
    }

    /**
     * 전이 없이 승인 요건(구조 단정)만 재사용한다. 시드 로더처럼 승인 상태로 곧장 생성하는 경로가
     * (전이 검증 없이도) [approve]와 같은 구조 가드레일을 통과했는지 확인할 때 쓴다. 위반 시 [InvalidApprovalStateException].
     */
    fun assertStructurallyApprovable() {
        validateApprovable()
    }

    /** 반려한다. 검수중에서만 가능하다. */
    fun reject() {
        validateTransition(
            approvalStatus == ApprovalStatus.IN_REVIEW,
            "반려는 검수중 상태에서만 할 수 있습니다. 현재 상태 = $approvalStatus",
        )
        approvalStatus = ApprovalStatus.REJECTED
    }

    /** 회수한다(승인 해제 → 서빙 중단). 승인 상태에서만 가능하다. */
    fun retract() {
        validateTransition(
            approvalStatus == ApprovalStatus.APPROVED,
            "회수는 승인 상태에서만 할 수 있습니다. 현재 상태 = $approvalStatus",
        )
        approvalStatus = ApprovalStatus.RETRACTED
    }

    private fun validateTransition(allowed: Boolean, message: String) {
        if (!allowed) {
            throw InvalidApprovalStateException(message)
        }
    }

    /** 승인 요건(결정적 구조 검증). 루브릭이 하나 이상이고 빈 포인트가 없어야 하며, 모범 설명이 존재해야 한다. */
    private fun validateApprovable() {
        if (rubricPoints.isEmpty() || rubricPoints.any { it.isBlank() }) {
            throw InvalidApprovalStateException(RUBRIC_REQUIRED_MESSAGE)
        }
        if (modelAnswer.isBlank()) {
            throw InvalidApprovalStateException(MODEL_ANSWER_REQUIRED_MESSAGE)
        }
    }

    /**
     * 점진 공개 힌트를 교체한다. **콘텐츠 재저작 갱신 전용**(기동 시 업서트, [watson.bytecs.interview.infrastructure.InterviewPromptDataLoader]).
     * 승인 상태·루브릭·개념 귀속 등 다른 필드에는 손대지 않는다. 빈 힌트 항목을 금지하는 생성자 불변식을 재사용해,
     * 갱신 경로로도 빈 문자열이 새어 들어가지 않게 한다.
     */
    fun updateHints(newHints: List<String>) {
        require(newHints.none { it.isBlank() }) { "면접 질문 힌트 항목은 비어 있을 수 없습니다." }
        hints = newHints
    }

    /** 이 면접 질문의 전체 힌트 수. 0이면 클라이언트가 힌트 진입점을 노출하지 않는다(Problem.hintCount 관례). */
    val hintCount: Int
        get() = hints.size

    /**
     * 앞에서부터 [count]개의 힌트(약→강)만 돌려준다. 재진입 복원·부분 공개에 쓴다.
     * 음수·초과 요청은 [0, hintCount]로 절단해, 미공개 힌트 본문이 절대 새어 나가지 않게 한다(no-leak, Problem 관례 미러).
     */
    fun revealedHints(count: Int): List<String> =
        hints.take(count.coerceIn(0, hints.size))

    companion object {
        const val RUBRIC_REQUIRED_MESSAGE = "승인하려면 비어 있지 않은 루브릭 핵심 포인트가 하나 이상 있어야 합니다."
        const val MODEL_ANSWER_REQUIRED_MESSAGE = "승인하려면 모범 설명이 있어야 합니다."

        // 수정 후 재검수 경로(반려·회수)를 포함해, 검수를 시작할 수 있는 상태들(Problem과 동일 규칙).
        private val REVIEWABLE_STATUSES =
            setOf(ApprovalStatus.DRAFT, ApprovalStatus.REJECTED, ApprovalStatus.RETRACTED)
    }
}
