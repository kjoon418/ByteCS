package watson.bytecs.report.presentation.request

import jakarta.validation.constraints.NotBlank

/**
 * 콘텐츠 오류 신고 요청. 07 시안대로 유형(필수, 단일 선택)과 상세 내용(선택)을 받는다.
 * category의 지원 값 검증은 도메인([ReportCategory])이 맡고(미지원 값 400), message는 비어 있어도 제출할 수 있다.
 */
data class CreateReportRequest(
    @field:NotBlank(message = "신고 유형은 필수입니다.")
    val category: String?,

    val message: String? = null,
)
