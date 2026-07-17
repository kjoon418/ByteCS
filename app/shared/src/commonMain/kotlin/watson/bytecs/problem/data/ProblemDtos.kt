package watson.bytecs.problem.data

import kotlinx.serialization.Serializable
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.EnrichmentItem

/**
 * 공용 심화(enrichment) 유선(wire) DTO. 레거시 추가 연습 문제 API가 폐기되고 나면서, 이 파일에는
 * session·scrap·extrastudy 슬라이스가 공유하는 [EnrichmentDto]·[EnrichmentItemDto]만 남는다(계약 §B).
 */

/**
 * '더 알아보기' 심화 정보(§5.7) 구조. 정답 처리 후에만 서버가 채워 보낸다(no-leak, 문제 배포 응답 비포함).
 * session·scrap·extrastudy 슬라이스의 DTO가 이 타입을 공유한다.
 */
@Serializable
internal data class EnrichmentDto(
    val title: String,
    val body: String,
    val items: List<EnrichmentItemDto> = emptyList(),
    val quote: String? = null,
) {
    fun toDomain(): Enrichment = Enrichment(
        title = title,
        body = body,
        items = items.map { it.toDomain() },
        quote = quote,
    )
}

/** [EnrichmentDto]의 보조 항목 하나. */
@Serializable
internal data class EnrichmentItemDto(
    val title: String,
    val description: String,
) {
    fun toDomain(): EnrichmentItem = EnrichmentItem(title, description)
}
