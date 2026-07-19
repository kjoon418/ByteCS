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

    @Test
    fun `userId가 null이어도 만들 수 있다(계정 삭제로 익명화된 신고)`() {
        // 신고는 콘텐츠 품질 운영 데이터라 신고자 계정이 삭제돼도 지우지 않고 userId만 null로 지운다(D10).
        val report = ContentReport(
            userId = null,
            problemId = 1L,
            category = ReportCategory.WRONG_ANSWER,
            message = null,
            createdAt = Instant.EPOCH,
        )

        assertThat(report.userId).isNull()
    }
}
