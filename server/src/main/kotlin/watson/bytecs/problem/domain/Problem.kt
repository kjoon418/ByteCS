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
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concept_id", nullable = false)
    val concept: Concept,

    @ElementCollection
    @CollectionTable(
        name = "problem_acceptable_answer",
        joinColumns = [JoinColumn(name = "problem_id")],
    )
    @Column(name = "answer", nullable = false)
    val acceptableAnswers: Set<String>,

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
) {
    init {
        require(questionText.isNotBlank()) { "문제 지문은 비어 있을 수 없습니다." }
        require(acceptableAnswers.isNotEmpty()) { "허용답 집합은 비어 있을 수 없습니다." }
        require(acceptableAnswers.none { it.isBlank() }) { "허용답은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

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
