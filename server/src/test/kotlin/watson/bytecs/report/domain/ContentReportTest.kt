package watson.bytecs.report.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ContentReportTest {

    @Test
    fun `상세 내용은 선택이므로 null로도 만들 수 있다`() {
        val report = ContentReport(
            userId = 1L,
            problemId = 1L,
            category = ReportCategory.WRONG_ANSWER,
            message = null,
            createdAt = Instant.EPOCH,
        )

        assertThat(report.message).isNull()
        assertThat(report.category).isEqualTo(ReportCategory.WRONG_ANSWER)
    }
}
