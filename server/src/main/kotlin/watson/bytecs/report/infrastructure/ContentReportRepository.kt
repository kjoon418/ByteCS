package watson.bytecs.report.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import watson.bytecs.report.domain.ContentReport

interface ContentReportRepository : JpaRepository<ContentReport, Long>
