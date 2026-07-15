package watson.bytecs.problem.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table

/**
 * 오답 교정 힌트 하나. (예상 오답 표기 집합, 교정 메시지)를 가지며 [Problem] 애그리거트에 소속된다.
 * 예상 오답 집합은 여러 값(중첩 컬렉션)이라 값 타입(@Embeddable)으로는 못 담으므로 자식 엔티티로 둔다.
 *
 * 매칭은 허용답과 **동일한 정규화**([AnswerText])를 재사용해 결정적으로 대조한다(AI 판정 없음).
 * 교정 메시지는 "왜 그 답이 아닌지"만 짚고 정답을 노출하지 않는다(무낙인·정답 비노출).
 */
@Entity
@Table(name = "misconception_hint")
class MisconceptionHint(
    @ElementCollection
    @CollectionTable(
        name = "misconception_hint_expected_answer",
        joinColumns = [JoinColumn(name = "misconception_hint_id")],
    )
    @Column(name = "answer", nullable = false)
    val expectedAnswers: Set<String>,

    @Column(name = "message", nullable = false, columnDefinition = "text")
    val message: String,
) {
    init {
        require(expectedAnswers.isNotEmpty()) { "예상 오답 집합은 비어 있을 수 없습니다." }
        require(expectedAnswers.none { it.isBlank() }) { "예상 오답 표기는 비어 있을 수 없습니다." }
        require(message.isNotBlank()) { "오답 교정 메시지는 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    /** 제출한 답이 이 교정 힌트의 예상 오답 집합과 정규화 후 일치하는지 결정적으로 판단한다. */
    fun matches(answer: AnswerText): Boolean =
        expectedAnswers.any { AnswerText(it).value == answer.value }
}
