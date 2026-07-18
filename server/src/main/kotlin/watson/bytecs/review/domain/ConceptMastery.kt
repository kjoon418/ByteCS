package watson.bytecs.review.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 한 사용자가 한 개념을 얼마나 정착시켰는지의 학습 상태(사용자 소유). 명세 §3.
 * 공용 콘텐츠(개념)와는 식별자(conceptId)로만 느슨하게 연결한다(SessionItem·Scrap 관례) — 사용자·개념 쌍마다 하나(유니크 제약).
 * 갱신은 결정적이며(같은 이력 → 같은 결과, LLM 없음), 레벨→간격 사다리로 다음 복습 시점을 파생한다.
 *
 * [구현 노트 — 오너 튜닝 대상(열린 질문)]
 *  간격 사다리 [INTERVAL_LADDER]: level 0~4 → [1, 3, 7, 14, 30]일.
 *  nextReviewDate = 풀이일 + 사다리[갱신 후 level]. 레벨 갱신 규칙은 [MasterySignal] KDoc 참고.
 *  이 사다리·규칙은 MVP 기본값(Leitner 5단계)이며 오너가 튜닝할 대상이다 — SM-2 등으로의 교체 비용을 낮게 유지한다.
 */
@Entity
@Table(
    name = "concept_mastery",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_concept_mastery_user_concept", columnNames = ["user_id", "concept_id"]),
    ],
)
class ConceptMastery private constructor(
    // 숙련도는 게스트/회원 구분 없이 userId 기준으로 격리된다(다른 사용자는 접근할 수 없다).
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "concept_id", nullable = false)
    val conceptId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set

    @Column(name = "level", nullable = false)
    var level: Int = MasterySignal.MIN_LEVEL
        protected set

    // 다음 복습 시점(간격 반복). applySolve로 항상 채워진 뒤 저장되고, 로드 시 Hibernate가 필드를 채운다.
    @Column(name = "next_review_date", nullable = false)
    lateinit var nextReviewDate: LocalDate
        protected set

    // 이 개념을 마지막으로 갱신한 문제 = 복습 문제 선정의 '그때 푼 그 문제'(기본 재출제 대상).
    @Column(name = "last_problem_id", nullable = false)
    var lastProblemId: Long = 0
        protected set

    /**
     * 정답 통과를 숙련도에 반영한다(애그리거트 주도).
     * 신호로 레벨을 갱신하고, 갱신된 레벨의 간격만큼 다음 복습 시점을 민다. 갱신한 문제 id를 '그때 푼 그 문제'로 남긴다.
     *
     * [관찰 — 레벨 경계에서의 신호 붕괴. 열린 질문 7(가중치)에 묶여 있음. 동작 변경 없음]
     * [MasterySignal.nextLevel]의 min/max 클램프 때문에, 레벨 경계에서는 서로 다른 도움 신호가 같은 다음 레벨로 붕괴한다:
     *  - level 0(MIN)에서는 AIDED(유지=0)와 REVEALED(max(0-1,0)=0)가 둘 다 0을 내어 구분되지 않는다.
     *  - level 4(MAX)에서는 UNAIDED(min(4+1,4)=4)와 AIDED(유지=4)가 둘 다 4를 내어 구분되지 않는다.
     * 즉 최저·최고 레벨에서는 '이번에 어떤 도움을 받았는지'가 다음 레벨 값에 반영되지 않는다.
     */
    fun applySolve(signal: MasterySignal, solvedOn: LocalDate, problemId: Long) {
        level = signal.nextLevel(level)
        nextReviewDate = solvedOn.plusDays(INTERVAL_LADDER[level].toLong())
        lastProblemId = problemId
    }

    companion object {
        // 간격 사다리(일). level(0~4)을 인덱스로 접근한다. [구현 노트 — 오너 튜닝 대상]
        val INTERVAL_LADDER = listOf(1, 3, 7, 14, 30)

        /**
         * 신규 개념 첫 정답 통과 시 행을 만든다. level 0에서 신호를 적용한다(무도움 → 1, 도움 → 0, 공개 → 0).
         */
        fun firstSolve(
            userId: Long,
            conceptId: Long,
            signal: MasterySignal,
            solvedOn: LocalDate,
            problemId: Long,
        ): ConceptMastery {
            val mastery = ConceptMastery(userId, conceptId)
            mastery.applySolve(signal, solvedOn, problemId)
            return mastery
        }
    }
}
