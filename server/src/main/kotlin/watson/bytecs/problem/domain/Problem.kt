package watson.bytecs.problem.domain

import jakarta.persistence.CascadeType
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
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

/**
 * 문제 풀이의 애그리거트 루트.
 * 정답 판정 로직(결정적 허용답 대조 + 근접 신호)을 도메인 내부에 캡슐화한다.
 */
@Entity
@Table(name = "problem")
class Problem(
    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    val questionText: String,

    /**
     * 이 문제가 다루는 개념들(하나 이상). 문제 N — M 개념 연결이며, 힌트 분기·복습의 근거가 된다.
     * [OrderColumn]으로 태깅 순서를 보존해, 첫 번째를 대표 개념으로 삼고 노출 순서를 결정적으로 고정한다.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "problem_concept",
        joinColumns = [JoinColumn(name = "problem_id")],
        inverseJoinColumns = [JoinColumn(name = "concept_id")],
    )
    @OrderColumn(name = "concept_index")
    val concepts: List<Concept>,

    @ElementCollection
    @CollectionTable(
        name = "problem_acceptable_answer",
        joinColumns = [JoinColumn(name = "problem_id")],
    )
    @Column(name = "answer", nullable = false)
    val acceptableAnswers: Set<String>,

    /**
     * 화면 표시용 대표 정답. 정답 공개·지난 문제·스크랩 상세에서 허용 표기를 나열하는 대신 이 하나만 보여준다(오너 결정 2026-07-16).
     *
     * 불변식: 정규화([AnswerText]) 기준으로 [acceptableAnswers]에 포함되어야 한다(init require).
     * 근거: 정답 공개 후 '따라 입력'에서 사용자가 화면의 대표 정답을 그대로 치면 반드시 통과해야 한다.
     * [AnswerText]는 구두점을 접지 않으므로, "스레드 (thread)"처럼 병기한 표기는 그 문자열 자체가 [acceptableAnswers]에도 등재돼 있어야 성립한다.
     */
    @Column(name = "representative_answer", nullable = false)
    val representativeAnswer: String,

    /**
     * 문제 유형. 근접(오탈자) 판정을 켤지 여부를 가른다.
     *
     * null(유형 미상)을 허용하는 이유:
     *  - 스키마가 `ddl-auto: update`라 기존 행을 백필할 수 없다. NOT NULL 컬럼 추가는 행이 있는 테이블에서 실패한다.
     *  - 미상일 때 근접 판정이 꺼지는 쪽(= 정확 일치만)으로 퇴화하므로, 유형을 빠뜨려도 안전한 방향으로만 틀린다.
     *    [DEFINITION_RECALL]을 기본값으로 두면 태깅을 빠뜨린 유도형에 근접 판정이 되살아나 버그가 재발한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "problem_type")
    val type: ProblemType? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    val difficulty: Difficulty? = null,

    @Column(name = "code_snippet", columnDefinition = "text")
    val codeSnippet: String? = null,

    @Column(name = "explanation", columnDefinition = "text")
    val explanation: String? = null,

    /**
     * 정답 처리 후에만 노출되는 구조화된 심화 정보('더 알아보기', 명세 §1 173~181행·시안 78~121행). 없어도 되는 큐레이션 콘텐츠(공용 자산).
     * 승인 게이트(명세 464행) 대상이나, 승인 파이프라인이 로드맵이라 MVP는 시딩된 콘텐츠를 승인 취급한다.
     */
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "enrichment_id")
    val enrichment: Enrichment? = null,

    /**
     * 약→강 순서의 힌트(0~N개). 순서는 소유 리스트의 인덱스([OrderColumn])로 보장한다.
     * 미공개 힌트 본문이 새어 나가지 않도록, 응답에는 [revealedHints]로 공개분만 잘라 싣는다(no-leak).
     */
    @ElementCollection
    @CollectionTable(
        name = "problem_hint",
        joinColumns = [JoinColumn(name = "problem_id")],
    )
    @OrderColumn(name = "hint_index")
    val hints: List<Hint> = emptyList(),

    /** 오답 교정 힌트(0~N개). 순서 무관(집합 매칭)이라 정렬을 두지 않는다. */
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    val misconceptionHints: List<MisconceptionHint> = emptyList(),

    /**
     * 생성 시점의 승인 상태. 신규 등록(관리자 반입·수동 등록)의 기본은 초안이다.
     * 시드 로더만 예외적으로 APPROVED로 생성한다(명세: MVP는 시딩분을 승인 취급 —
     * 시드는 로더 테스트의 no-leak·분포 스윕이 구조를 전수 검증하므로 전이 검증을 거치지 않는다).
     */
    approvalStatus: ApprovalStatus = ApprovalStatus.DRAFT,
) {
    init {
        require(questionText.isNotBlank()) { "문제 지문은 비어 있을 수 없습니다." }
        require(concepts.isNotEmpty()) { "문제는 하나 이상의 개념에 연결되어야 합니다." }
        require(acceptableAnswers.isNotEmpty()) { "허용답 집합은 비어 있을 수 없습니다." }
        require(acceptableAnswers.none { it.isBlank() }) { "허용답은 비어 있을 수 없습니다." }
        require(representativeAnswer.isNotBlank()) { "대표 정답은 비어 있을 수 없습니다." }
        // 대표 정답을 화면에서 그대로 따라 입력하면 통과해야 하므로, 정규화 기준으로 허용답 집합에 있어야 한다.
        require(AnswerText(representativeAnswer).value in acceptableAnswers.map { AnswerText(it).value }) {
            "대표 정답은 정규화 기준으로 허용답 집합에 포함되어야 합니다."
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    /**
     * 콘텐츠 승인 상태(명세 '콘텐츠 승인 상태' 절). **승인(APPROVED)만 서빙 후보 쿼리에 잡힌다**
     * ([watson.bytecs.problem.infrastructure.ProblemRepository]의 승인 필터).
     * 전이는 아래 전이 메서드로만 일어나며, 허용되지 않는 전이는 [InvalidApprovalStateException]으로 거부된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    var approvalStatus: ApprovalStatus = approvalStatus
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
     * 승인한다. 검수중에서만 가능하며, 결정적 구조 검증(콘텐츠 신뢰성 가드레일)을 통과해야 한다:
     *  - **문제 유형 태깅 존재**(명세 수용 기준 23 — 유형은 근접 신호·복습 선정 분기의 입력이라 없으면 동작이 정의되지 않는다).
     *  - **힌트·오답 교정 힌트의 자기 정답 비노출(no-leak)** — 학습 효과 보존.
     * (허용답 비어있지 않음·개념 태깅 존재·대표 정답 포함은 생성자 init 불변식이 이미 보장한다.)
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
     * (전이 검증 없이도) [approve]와 같은 구조 가드레일(유형 태깅·no-leak)을 통과했는지 확인할 때 쓴다.
     * 위반 시 [InvalidApprovalStateException]을 던진다.
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

    /**
     * 승인 요건(결정적 구조 검증). 판정 기준은 시드 no-leak 스윕(ProblemDataLoaderTest)과 동일하다 —
     * 정규화([AnswerText])한 허용답이 힌트 본문·교정 메시지(소문자화)에 부분 문자열로 포함되면 위반.
     */
    private fun validateApprovable() {
        if (type == null) {
            throw InvalidApprovalStateException(TYPE_REQUIRED_MESSAGE)
        }

        val normalizedAnswers = acceptableAnswers.map { AnswerText(it).value }
        val curatedTexts = hints.map { it.text } + misconceptionHints.map { it.message }
        curatedTexts.forEach { text ->
            val normalizedText = text.lowercase()
            if (normalizedAnswers.any { it in normalizedText }) {
                throw InvalidApprovalStateException(ANSWER_LEAK_MESSAGE)
            }
        }
    }

    /** 개념 이름을 태깅 순서(대표 개념이 먼저)로 돌려준다. 정답 처리 후 개념 노출에 쓴다. */
    fun conceptNames(): List<String> = concepts.map { it.name }

    /** 개념 식별자를 태깅 순서로 돌려준다. 숙련도 갱신(기능 3)이 이 문제의 모든 개념에 적용될 때 쓴다. */
    fun conceptIds(): List<Long> = concepts.map { it.id }

    /**
     * 이 문제의 대표 분류(명세 §7 '대표 분류 원칙', 결정 2026-07-17).
     * 문제가 여러 개념·카테고리에 걸칠 수 있어도, 화면 표시·이력 분류는 **대표 개념(첫 번째 개념)의 카테고리**
     * 하나로 결정적으로 도출한다([concepts]가 [OrderColumn]으로 순서를 보존하므로 같은 입력에는 항상 같은 결과다).
     * 대표 개념이 미분류(null)면 대표 분류도 null이며, 이는 화면에서 '준비 중'으로 처리된다.
     */
    fun representativeCategory(): ProblemCategory? = concepts.first().category

    /**
     * 제출한 답을 결정적으로 판정한다.
     *  1. 정규화 후 허용답과 정확히 일치하면 CORRECT.
     *  2. 오탈자 수준(편집거리 임계 내)으로 가까우면 NEAR_MISS. 단, [isNearMissCandidate]를 만족할 때만.
     *  3. 그 외에는 MISMATCH.
     * 정규화는 [AnswerText]로 통일해, 정확 일치와 근접 판정이 같은 기준을 공유한다.
     */
    fun judge(answer: AnswerText): Judgement {
        val normalizedAcceptableAnswers = acceptableAnswers.map { AnswerText(it).value }

        if (answer.value in normalizedAcceptableAnswers) {
            return Judgement.CORRECT
        }

        val isNearMiss = normalizedAcceptableAnswers.any { acceptable ->
            isNearMissCandidate(acceptable) &&
                EditDistance.levenshtein(answer.value, acceptable) <= nearMissThreshold(acceptable.length)
        }
        return if (isNearMiss) Judgement.NEAR_MISS else Judgement.MISMATCH
    }

    /** 이 문제의 전체 힌트 수. 0이면 클라이언트가 힌트 진입점을 노출하지 않는다(눌러도 아무것도 없는 버튼 금지). */
    val hintCount: Int
        get() = hints.size

    /**
     * 앞에서부터 [count]개의 힌트(약→강)만 돌려준다. 재진입 복원·부분 공개에 쓴다.
     * 음수·초과 요청은 [0, hintCount]로 절단해, 미공개 힌트 본문이 절대 새어 나가지 않게 한다(no-leak).
     */
    fun revealedHints(count: Int): List<Hint> =
        hints.take(count.coerceIn(0, hints.size))

    /**
     * 제출 답을 판정하고, 예상 오답에 매칭되면 오답 교정 힌트를 함께 산출한다([AttemptOutcome]).
     *  - CORRECT면 그대로 반환한다(정답에는 교정 힌트를 붙이지 않는다).
     *  - 비정답이 어떤 오답 교정 힌트의 예상 오답 집합과 정규화 후 일치하면, 판정을 **MISMATCH로 확정**하고(근접보다 우선)
     *    그 교정 메시지를 싣는다. 예상 오답에 매칭됐다면 그것은 '다른 개념의 답'이지 오타가 아니므로, 근접(NEAR_MISS)으로
     *    알려주면 틀린 유도를 맞았다고 알려주는 셈이 된다(§1.4 근접 신호의 취지와 정합).
     *  - 매칭되지 않은 비정답은 판정을 그대로 두고 교정 힌트를 싣지 않는다(막다른 길 없음 — 일반 재시도로 흐른다).
     * 무낙인은 유지된다 — 교정 힌트가 떠도 오답으로 확정하지 않으며 정답을 노출하지 않는다.
     */
    fun evaluate(answer: AnswerText): AttemptOutcome {
        val judgement = judge(answer)
        if (judgement == Judgement.CORRECT) {
            return AttemptOutcome(Judgement.CORRECT, null)
        }

        val misconceptionHint = misconceptionHints.firstOrNull { it.matches(answer) }?.message
        val finalJudgement = if (misconceptionHint != null) Judgement.MISMATCH else judgement
        return AttemptOutcome(finalJudgement, misconceptionHint)
    }

    /**
     * 이 허용답에 근접 판정을 적용해도 되는지 판단한다. 두 관문을 모두 통과해야 한다.
     *
     * 1. **유형이 정의 재생형인가.** 근접 신호는 "편집거리가 작다 ⇒ 오타다"를 가정한다.
     *    정의 재생형은 정답이 자연어 단어(개념 이름)라 개념명끼리 편집거리가 멀고(`TCP`↔`UDP` = 2),
     *    편집거리 1은 실제로 오타다(`collsion` → `collision`). 유도형은 정답이 수식·숫자처럼
     *    밀집한 공간의 한 점이라 이웃이 전부 유효한 다른 답이고, 한 글자가 곧 의미(지수·차수·자릿수)다.
     *    `o(n²)`에 `o(n)`은 오타가 아니라 이중 반복문을 하나로 잘못 센 오답이므로,
     *    근접으로 알려주면 틀린 유도를 맞았다고 알려주는 셈이 된다. 유형 미상(null)도 같은 이유로 제외한다.
     * 2. **허용답이 충분히 긴가.** [MIN_NEAR_MISS_LENGTH] 참고. 유형과 별개로 필요한 조건이다.
     */
    private fun isNearMissCandidate(acceptable: String): Boolean =
        type == ProblemType.DEFINITION_RECALL && acceptable.length >= MIN_NEAR_MISS_LENGTH

    companion object {
        const val TYPE_REQUIRED_MESSAGE = "승인하려면 문제 유형(정의 재생형/유도형) 태깅이 있어야 합니다."
        const val ANSWER_LEAK_MESSAGE = "힌트·오답 교정 힌트가 정답을 노출하는 문제는 승인할 수 없습니다."

        // 수정 후 재검수 경로(반려·회수)를 포함해, 검수를 시작할 수 있는 상태들.
        private val REVIEWABLE_STATUSES =
            setOf(ApprovalStatus.DRAFT, ApprovalStatus.REJECTED, ApprovalStatus.RETRACTED)

        // 1~2자 답은 근접 판정을 아예 하지 않는다.
        // (편집거리 1이 '전혀 다른 답'과 구분되지 않아, 근접이 정답의 길이·모양을 흘리기 때문)
        private const val MIN_NEAR_MISS_LENGTH = 3

        // 짧은 답은 편집거리 1(오타 1개)까지만 근접으로 본다.
        private const val SHORT_ANSWER_MAX_LENGTH = 7
        private const val SHORT_ANSWER_THRESHOLD = 1

        // 긴 답은 오타 2개까지 근접으로 완화하되, 보수적으로 유지한다.
        private const val LONG_ANSWER_THRESHOLD = 2

        private fun nearMissThreshold(acceptableLength: Int): Int =
            if (acceptableLength <= SHORT_ANSWER_MAX_LENGTH) SHORT_ANSWER_THRESHOLD else LONG_ANSWER_THRESHOLD
    }
}
