package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.problem.domain.Concept

interface ConceptRepository : JpaRepository<Concept, Long> {

    /** 이름 기준 find-or-create의 조회 절반. 개념 이름은 unique라 문제 간 공유의 근거가 된다. */
    fun findByName(name: String): Concept?
}
