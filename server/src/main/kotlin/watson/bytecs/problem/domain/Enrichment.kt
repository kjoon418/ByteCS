package watson.bytecs.problem.domain

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

/**
 * 정답 처리 이후에만 노출되는 구조화된 심화 정보('더 알아보기', 시안 78~121행). [Problem] 애그리거트에 소속되며 문제와 생명주기를 함께한다(@OneToOne, cascade·orphanRemoval).
 *  - title: 카드 제목(예: "왜 충돌이 발생할까요?")
 *  - body: 리드 문단
 *  - items: 보조 항목 카드(제목+설명) 0..N개, 표시 순서 보존
 *  - quote: 인용 카드(선택)
 *
 * [Problem.enrichment]에서 nullable이며, 없으면 노출 자체가 생략된다(graceful).
 * 값 객체 성격이지만 순서 있는 items 컬렉션을 품은 nullable @Embedded는 Hibernate가 빈 임베더블을 non-null로 되살려
 * ('없음'을 표현하지 못함) 별도 엔티티로 두고 FK(enrichment_id)의 유무로 존재를 정직하게 판정한다.
 */
@Entity
@Table(name = "problem_enrichment")
class Enrichment(
    @Column(name = "title", nullable = false, columnDefinition = "text")
    val title: String,

    @Column(name = "body", nullable = false, columnDefinition = "text")
    val body: String,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "problem_enrichment_item",
        joinColumns = [JoinColumn(name = "enrichment_id")],
    )
    @OrderColumn(name = "item_index")
    val items: List<EnrichmentItem> = emptyList(),

    @Column(name = "quote", columnDefinition = "text")
    val quote: String? = null,
) {
    init {
        require(title.isNotBlank()) { "심화 정보 제목은 비어 있을 수 없습니다." }
        require(body.isNotBlank()) { "심화 정보 본문은 비어 있을 수 없습니다." }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0
        protected set
}
