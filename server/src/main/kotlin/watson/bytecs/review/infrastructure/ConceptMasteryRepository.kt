package watson.bytecs.review.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.review.domain.ConceptMastery
import java.time.LocalDate

interface ConceptMasteryRepository : JpaRepository<ConceptMastery, Long> {

    /** 사용자·개념 쌍의 숙련도. 갱신 시 신규/기존을 가르는 조회 축(유니크 제약과 짝을 이룬다). */
    fun findByUserIdAndConceptId(userId: Long, conceptId: Long): ConceptMastery?

    /**
     * 복습 시점이 도래한(nextReviewDate <= 오늘) 그 사용자의 숙련도를,
     * 도래 우선(nextReviewDate asc)·개념 id 순으로 결정적으로 조회한다(§3 세션 편입).
     */
    fun findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscConceptIdAsc(
        userId: Long,
        date: LocalDate,
    ): List<ConceptMastery>

    /** 계정 삭제 시 그 사용자의 숙련도를 일괄 삭제한다(학습 상태 삭제 흐름 편입 — 파생 쿼리). */
    fun deleteByUserId(userId: Long)
}
