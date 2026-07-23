package watson.bytecs.review.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.review.domain.ConceptMastery
import java.time.LocalDate

interface ConceptMasteryRepository : JpaRepository<ConceptMastery, Long> {

    /** 사용자·개념 쌍의 숙련도. 갱신 시 신규/기존을 가르는 조회 축(유니크 제약과 짝을 이룬다). */
    fun findByUserIdAndConceptId(userId: Long, conceptId: Long): ConceptMastery?

    /**
     * 그 사용자가 학습 이력을 가진(정답 통과로 숙련도 행이 존재하는) 개념 id를 조회한다.
     * 연결 문제 하드 게이트(계획 §3.2)가 '구성 개념을 모두 만난 적 있는가'를 판정하는 입력이다 —
     * **레벨과 무관하게** 행 존재만 본다(승급 임계 레벨≥1과 다른 기준). 문제별 개별 조회(N+1)를
     * 피하려고 보유 개념을 사용자당 1회로 펼쳐, 애플리케이션에서 후보 필터에 쓴다.
     */
    @Query("select cm.conceptId from ConceptMastery cm where cm.userId = :userId")
    fun findConceptIdsByUserId(userId: Long): List<Long>

    /**
     * 그 사용자가 승급 임계(레벨 ≥ [minLevel])에 도달한 개념 id를 조회한다(면접 세션 승급 후보 산정, 계획 §3.3 · DI8).
     * 레벨은 매 조회 시점의 최신 값이라, 레벨이 0으로 떨어지면 다음 조회에서 자연히 후보에서 빠진다(별도 상태 관리 없음).
     */
    @Query("select cm.conceptId from ConceptMastery cm where cm.userId = :userId and cm.level >= :minLevel")
    fun findConceptIdsByUserIdAndLevelGreaterThanEqual(userId: Long, minLevel: Int): List<Long>

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
