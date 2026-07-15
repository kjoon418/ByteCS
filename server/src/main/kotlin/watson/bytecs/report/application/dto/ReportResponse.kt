package watson.bytecs.report.application.dto

import java.time.Instant

/**
 * 신고 접수 결과. 큐레이터 처리(수정·회수)는 운영 영역이라, 클라이언트에는 접수 사실만 확인해 준다.
 */
data class ReportResponse(
    val id: Long,
    val problemId: Long,
    val category: String,
    val createdAt: Instant,
)
