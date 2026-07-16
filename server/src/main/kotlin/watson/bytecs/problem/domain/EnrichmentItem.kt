package watson.bytecs.problem.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 심화 정보('더 알아보기')의 보조 항목 카드 하나(시안 87~102행). 제목 + 설명으로 이루어진다.
 * [Enrichment]의 items 컬렉션에 값으로 소속되며, 표시 순서는 소유 리스트의 순서(@OrderColumn)로 보장한다.
 */
@Embeddable
class EnrichmentItem(
    @Column(name = "item_title", nullable = false, columnDefinition = "text")
    val title: String,

    @Column(name = "item_description", nullable = false, columnDefinition = "text")
    val description: String,
) {
    init {
        require(title.isNotBlank()) { "심화 정보 항목 제목은 비어 있을 수 없습니다." }
        require(description.isNotBlank()) { "심화 정보 항목 설명은 비어 있을 수 없습니다." }
    }
}
