package watson.bytecs.problem.domain

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

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty")
    val difficulty: Difficulty? = null,

    @Column(name = "code_snippet", columnDefinition = "text")
    val codeSnippet: String? = null,

    @Column(name = "explanation", columnDefinition = "text")
    val explanation: String? = null,
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
     *  2. 오탈자 수준(편집거리 임계 내)으로 가까우면 NEAR_MISS.
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

    companion object {
        // 1~2자 답은 근접 판정을 아예 하지 않는다.
        // (편집거리 1이 '전혀 다른 답'과 구분되지 않아, 근접이 정답의 길이·모양을 흘리기 때문)
        private const val MIN_NEAR_MISS_LENGTH = 3

        // 짧은 답은 편집거리 1(오타 1개)까지만 근접으로 본다.
        private const val SHORT_ANSWER_MAX_LENGTH = 7
        private const val SHORT_ANSWER_THRESHOLD = 1

        // 긴 답은 오타 2개까지 근접으로 완화하되, 보수적으로 유지한다.
        private const val LONG_ANSWER_THRESHOLD = 2

        private fun isNearMissCandidate(acceptable: String): Boolean =
            acceptable.length >= MIN_NEAR_MISS_LENGTH

        private fun nearMissThreshold(acceptableLength: Int): Int =
            if (acceptableLength <= SHORT_ANSWER_MAX_LENGTH) SHORT_ANSWER_THRESHOLD else LONG_ANSWER_THRESHOLD
    }
}
