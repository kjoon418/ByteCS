package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemType

interface ProblemRepository : JpaRepository<Problem, Long> {

    /** 스테이트리스 추가 연습에서는 가장 먼저 등록된 문제를 반환한다. */
    fun findFirstByOrderByIdAsc(): Problem?

    /**
     * 세션 배정 후보를 id 오름차순으로 조회한다(순서를 결정적으로 고정).
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
}
