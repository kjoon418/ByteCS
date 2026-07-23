package watson.bytecs.interview.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.interview.domain.InterviewReadiness

interface InterviewReadinessRepository : JpaRepository<InterviewReadiness, Long> {

    /** 사용자·개념 쌍의 준비도(upsert 조회 축, 유니크 제약과 짝을 이룬다). */
    fun findByUserIdAndConceptId(userId: Long, conceptId: Long): InterviewReadiness?

    /** 승급 후보 개념들의 준비도를 한 번에 조회한다(우선순위 정렬 — 미검증>부분>검증됨. N+1 회피). */
    fun findByUserIdAndConceptIdIn(userId: Long, conceptIds: Collection<Long>): List<InterviewReadiness>

    /** 계정 삭제 시 그 사용자의 준비도를 일괄 삭제한다(학습 상태 삭제 흐름 편입). */
    fun deleteByUserId(userId: Long)
}
