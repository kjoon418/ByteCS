package watson.bytecs.problem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 문제가 다루는 CS 개념. 정답 공개 시에만 사용자에게 노출된다.
 */
@Entity
@Table(name = "concept")
class Concept(
    @Column(nullable = false, unique = true)
    val name: String,

    @Column(columnDefinition = "text")
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "개념 이름은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
