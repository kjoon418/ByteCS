package watson.bytecs.problem.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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

    /**
     * 이 개념이 속하는 CS 대분류(명세 §7, 결정 2026-07-17). 문제의 대표 분류는 대표 개념(첫 번째 개념)의
     * 이 값에서 결정적으로 도출된다([Problem.representativeCategory]).
     *
     * null(미분류)을 허용하는 이유:
     *  - 스키마가 `ddl-auto: update`라 기존 행을 백필할 수 없다. NOT NULL 컬럼 추가는 행이 있는 테이블에서 실패한다.
     *  - 백필은 AI 일괄 분류 + 오너 검수로 별도 진행되며([기능 7]), 그 전까지 미분류 개념은 '준비 중'으로 안전하게 퇴화한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    val category: ProblemCategory? = null,
) {
    init {
        require(name.isNotBlank()) { "개념 이름은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
