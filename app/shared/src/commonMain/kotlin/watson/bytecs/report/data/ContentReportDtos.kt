package watson.bytecs.report.data

import kotlinx.serialization.Serializable

/**
 * 콘텐츠 오류 신고 API 계약과 1:1 대응하는 유선(wire) DTO.
 */

/**
 * `POST /api/problems/{problemId}/reports` 요청 본문.
 * [category]는 필수(서버가 미지원 코드를 거부), [message]는 선택(비어도 제출 가능).
 */
@Serializable
internal data class ContentReportRequestDto(
    val category: String,
    val message: String? = null,
)
