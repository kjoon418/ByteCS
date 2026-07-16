package watson.bytecs.scrap.data

import kotlinx.serialization.Serializable
import watson.bytecs.scrap.ScrapDetail
import watson.bytecs.scrap.ScrapListItem

/**
 * 백엔드 `/api/scraps` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리한다.
 */

/**
 * `GET /api/scraps` 응답 항목.
 * question은 스크랩한 문제가 회수·삭제됐으면 null이다('더 이상 볼 수 없음' — 서버 [결정], 도메인 명세 406행).
 */
@Serializable
internal data class ScrapListItemDto(
    val problemId: Long,
    val question: String? = null,
    val scrappedAt: String,
) {
    fun toDomain(): ScrapListItem = ScrapListItem(problemId, question, scrappedAt)
}

/**
 * `GET /api/scraps/{problemId}` 응답. 재열람이므로 모범답안·해설을 담는다.
 * [concepts]는 태깅 순서를 보존한 개념 목록(첫 번째가 대표 개념).
 */
@Serializable
internal data class ScrapDetailDto(
    val problemId: Long,
    val question: String,
    val codeSnippet: String? = null,
    val concepts: List<String>,
    val explanation: String? = null,
    // 화면 표시용 대표 정답 하나. 허용답 나열은 응답에서 사라졌다([2026-07-16] 오너 결정).
    val representativeAnswer: String,
    val enrichment: String? = null,
) {
    fun toDomain(): ScrapDetail = ScrapDetail(
        problemId = problemId,
        question = question,
        codeSnippet = codeSnippet,
        concepts = concepts,
        explanation = explanation,
        representativeAnswer = representativeAnswer,
        enrichment = enrichment,
    )
}
