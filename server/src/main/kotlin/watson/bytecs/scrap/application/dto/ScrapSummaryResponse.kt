package watson.bytecs.scrap.application.dto

import java.time.Instant

/**
 * 스크랩 목록의 한 항목. 본인 것만 실린다(사용자 격리).
 * question은 스크랩한 문제가 회수·삭제됐으면 null이다('더 이상 볼 수 없음' — 도메인 [결정]).
 */
data class ScrapSummaryResponse(
    val problemId: Long,
    val question: String?,
    val scrappedAt: Instant,
)
