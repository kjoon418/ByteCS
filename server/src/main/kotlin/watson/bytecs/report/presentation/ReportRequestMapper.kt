package watson.bytecs.report.presentation

import org.springframework.stereotype.Component
import watson.bytecs.report.domain.ReportCategory
import watson.bytecs.report.presentation.request.CreateReportRequest

/**
 * 원시 값 요청을 도메인 타입으로 변환한다.
 * category의 존재는 Bean Validation(@NotBlank)이, 지원 값 여부는 [ReportCategory.from]이 보장한다(미지원 값 400).
 */
@Component
class ReportRequestMapper {

    fun toCategory(request: CreateReportRequest): ReportCategory =
        ReportCategory.from(request.category!!)
}
