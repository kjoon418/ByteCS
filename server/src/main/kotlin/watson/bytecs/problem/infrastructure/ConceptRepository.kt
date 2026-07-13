package watson.bytecs.problem.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.problem.domain.Concept

interface ConceptRepository : JpaRepository<Concept, Long>
