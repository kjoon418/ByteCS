package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.problem.domain.Problem

interface ProblemRepository : JpaRepository<Problem, Long> {

    /** 현재는 세션 로직이 없으므로 가장 먼저 등록된 문제를 반환한다. */
    fun findFirstByOrderByIdAsc(): Problem?
}
