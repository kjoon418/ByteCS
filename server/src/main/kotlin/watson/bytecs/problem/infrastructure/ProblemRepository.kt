package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

interface ProblemRepository : JpaRepository<Problem, Long> {

    /**
     * 세션 배정·추가 연습 후보를 id 오름차순으로 조회한다(선정은 애플리케이션에서 무작위로 하되, 조회 순서는 고정).
     * 배정에는 id만 필요하므로 전체 엔티티를 로딩하지 않고 id만 프로젝션한다.
     */
    @Query("select p.id from Problem p order by p.id asc")
    fun findAllIdsOrderByIdAsc(): List<Long>

    /**
     * 복습 문제 선정에서 유도형 예외를 가릴 때 쓴다. 문제가 없거나 유형 미상이면 null(둘 다 예외 미적용으로 안전하게 퇴화).
     * 유형만 필요하므로 엔티티를 로딩하지 않고 프로젝션한다.
     */
    @Query("select p.type from Problem p where p.id = :id")
    fun findTypeById(id: Long): ProblemType?

    /**
     * 한 개념에 태깅된 문제 id를 오름차순으로 조회한다(유도형 '아직 안 낸 다른 문제' 후보 선정).
     * @ManyToMany 조인이라 id만 프로젝션해 필요한 것만 가져온다.
     */
    @Query("select p.id from Problem p join p.concepts c where c.id = :conceptId order by p.id asc")
    fun findIdsByConceptIdOrderByIdAsc(conceptId: Long): List<Long>

    /**
     * 카테고리별 학습 이력 조회 전용(N+1 회피, [watson.bytecs.study.application.CategoryHistoryService]).
     * `representativeCategory()`(개념)·`conceptNames()`(개념)·엔리치먼트를 모두 화면에 실어야 해서,
     * 지연 로딩을 그대로 두면 문제당 최대 2쿼리가 추가로 발생한다(N+1).
     * concepts는 [jakarta.persistence.OrderColumn]로 순서를 갖는 인덱스 컬렉션이라, 다른 컬렉션 없이
     * 단일 연관(enrichment, @OneToOne)과 함께 페치해도 하이버네이트의 MultipleBagFetchException 대상이 아니다.
     * concepts 조인으로 같은 문제 행이 개념 수만큼 늘어날 수 있어 `distinct`로 접는다.
     */
    @Query("select distinct p from Problem p left join fetch p.concepts left join fetch p.enrichment where p.id in :ids")
    fun findAllByIdWithConceptsAndEnrichment(ids: Collection<Long>): List<Problem>
}
