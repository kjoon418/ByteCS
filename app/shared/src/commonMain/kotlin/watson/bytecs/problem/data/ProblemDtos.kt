package watson.bytecs.problem.data

import kotlinx.serialization.Serializable
import watson.bytecs.problem.AttemptResult
import watson.bytecs.problem.Enrichment
import watson.bytecs.problem.EnrichmentItem
import watson.bytecs.problem.JudgeResult
import watson.bytecs.problem.ProblemView

/**
 * 백엔드 API 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되도록 한다.
 */

/**
 * '더 알아보기' 심화 정보(§5.7) 구조. 정답 처리 후에만 서버가 채워 보낸다(no-leak, 문제 배포 응답 비포함).
 * session·scrap 슬라이스의 DTO도 이 타입을 공유한다(계약 §B).
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

/** `GET /api/problems/next` 응답. */
@Serializable
internal data class NextProblemDto(
    val id: Long,
    val question: String,
    val difficulty: String? = null,
    val codeSnippet: String? = null,
) {
    fun toDomain(): ProblemView = ProblemView(
        id = id,
        question = question,
        difficulty = difficulty,
        codeSnippet = codeSnippet,
    )
}

/** `POST /api/problems/{id}/attempts` 요청 본문. */
@Serializable
internal data class AttemptRequestDto(
    val answer: String,
)

/**
 * `POST /api/problems/{id}/attempts` 응답.
 * concepts·explanation·enrichment는 서버가 CORRECT일 때만 채워 보낸다(무낙인·정답 비노출). concepts는 태깅 순서를
 * 보존한 목록(첫 번째가 대표 개념) — 문제가 개념 N—M으로 태깅될 수 있다. enrichment는 '더 알아보기'(§5.7,
 * 선택 콘텐츠 — 없어도 됨).
 */
@Serializable
internal data class AttemptResponseDto(
    val result: String,
    val concepts: List<String>? = null,
    val explanation: String? = null,
    val enrichment: EnrichmentDto? = null,
    // 화면 표시용 대표 정답. 서버가 CORRECT일 때만 채워 보낸다(무낙인·정답 비노출 연장).
    val representativeAnswer: String? = null,
) {
    fun toDomain(): AttemptResult = AttemptResult(
        // 서버 enum명과 대소문자 무시 대조. 미지값은 명확한 예외로 올려(여전히 뷰모델이 catch)
        // 판정 오류를 네트워크 오류로 오인하지 않게 한다.
        result = JudgeResult.entries.find { it.name.equals(result, ignoreCase = true) }
            ?: throw IllegalStateException("알 수 없는 판정 결과: $result"),
        concepts = concepts,
        explanation = explanation,
        enrichment = enrichment?.toDomain(),
        representativeAnswer = representativeAnswer,
    )
}
