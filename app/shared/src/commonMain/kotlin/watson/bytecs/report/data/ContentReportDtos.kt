package watson.bytecs.report.data

import kotlinx.serialization.Serializable

/**
 * 콘텐츠 오류 신고 API 계약과 1:1 대응하는 유선(wire) DTO.
 */

/** `POST /api/problems/{problemId}/reports` 요청 본문. 서버는 blank message를 거부한다. */
@Serializable
internal data class ContentReportRequestDto(
    val message: String,
)
