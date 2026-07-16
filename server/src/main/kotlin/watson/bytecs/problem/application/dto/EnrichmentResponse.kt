package watson.bytecs.problem.application.dto

import watson.bytecs.problem.domain.Enrichment

/**
 * 구조화된 심화 정보('더 알아보기') 응답(시안 78~121행). 정답 처리 이후 응답에만 실린다(no-leak).
 * title(제목) + body(리드 문단) + items(보조 항목 카드, 순서 보존) + quote(인용, 선택).
 * 정답 처리·정답 공개·지난 문제·스크랩 상세가 같은 구조를 공유하도록 한곳에 둔다.
 */
data class EnrichmentResponse(
    val title: String,
    val body: String,
    val items: List<EnrichmentItemResponse>,
    val quote: String?,
) {
    companion object {
        fun from(enrichment: Enrichment): EnrichmentResponse =
            EnrichmentResponse(
                title = enrichment.title,
                body = enrichment.body,
                items = enrichment.items.map { EnrichmentItemResponse(it.title, it.description) },
                quote = enrichment.quote,
            )
    }
}

/** 심화 정보의 보조 항목 카드 하나(제목 + 설명). */
data class EnrichmentItemResponse(
    val title: String,
    val description: String,
)
