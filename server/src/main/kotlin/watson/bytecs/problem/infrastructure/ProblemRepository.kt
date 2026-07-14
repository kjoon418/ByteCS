package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import watson.bytecs.problem.domain.Problem

interface ProblemRepository : JpaRepository<Problem, Long> {

    /** 스테이트리스 추가 연습에서는 가장 먼저 등록된 문제를 반환한다. */
    fun findFirstByOrderByIdAsc(): Problem?

    /**
     * 세션 배정 후보를 id 오름차순으로 조회한다(순서를 결정적으로 고정).
     * 배정에는 id만 필요하므로 전체 엔티티를 로딩하지 않고 id만 프로젝션한다.
     */
    @Query("select p.id from Problem p order by p.id asc")
    fun findAllIdsOrderByIdAsc(): List<Long>
}
