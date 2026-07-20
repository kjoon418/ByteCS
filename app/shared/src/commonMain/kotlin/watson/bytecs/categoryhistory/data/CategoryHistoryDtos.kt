package watson.bytecs.categoryhistory.data

import kotlinx.serialization.Serializable
import watson.bytecs.categoryhistory.CategoryHistoryGroup
import watson.bytecs.categoryhistory.CategoryHistoryItem
import watson.bytecs.problem.JudgeResult
import watson.bytecs.problem.data.EnrichmentDto

/**
 * 백엔드 `/api/learning-history/categories` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해,
 * API 형태가 바뀌어도 매핑 한곳만 고치면 되게 한다. 심화 구조는 공용 [EnrichmentDto]를 재사용한다
 * (계약 §B — session·scrap과 공유).
 */

/** 판정 문자열을 [JudgeResult]로 매핑. 미지값은 명확한 예외로 올려 네트워크 오류와 구분한다(다른 슬라이스와 동일). */
private fun String.toJudgeResult(): JudgeResult =
    JudgeResult.entries.find { it.name.equals(this, ignoreCase = true) }
        ?: throw IllegalStateException("알 수 없는 판정 결과: $this")

/**
 * 카테고리별 이력의 한 항목. concepts는 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 */
@Serializable
internal data class CategoryHistoryItemDto(
    val problemId: Long,
    val question: String,
    val codeSnippet: String? = null,
    val difficulty: String? = null,
    // '내가 쓴 답'은 화면에서 제거됐으나(오너 결정) 유선 호환을 위해 필드는 남기고 매핑하지 않는다 —
    // 서버가 계속 내려주므로 삭제하면 역직렬화가 깨질 수 있다.
    val submittedAnswer: String? = null,
    val result: String,
    val concepts: List<String>,
    val explanation: String? = null,
    val enrichment: EnrichmentDto? = null,
    val representativeAnswer: String,
) {
    fun toDomain(): CategoryHistoryItem = CategoryHistoryItem(
        problemId = problemId,
        question = question,
        codeSnippet = codeSnippet,
        difficulty = difficulty,
        result = result.toJudgeResult(),
        concepts = concepts,
        explanation = explanation,
        representativeAnswer = representativeAnswer,
        enrichment = enrichment?.toDomain(),
    )
}

/**
 * `GET /api/learning-history/categories` 응답의 한 그룹. 8개 고정 대분류 전체가 항상 실리며,
 * 문제가 없는 카테고리는 [items]가 빈 목록이다(클라는 '준비 중'으로 렌더).
 */
@Serializable
internal data class CategoryHistoryGroupDto(
    val category: String,
    val items: List<CategoryHistoryItemDto>,
) {
    fun toDomain(): CategoryHistoryGroup = CategoryHistoryGroup(category, items.map { it.toDomain() })
}
