package watson.bytecs.report.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ReportCategoryTest {

    @Test
    fun `지원하는 코드는 해당 유형으로 변환된다`() {
        assertThat(ReportCategory.from("WRONG_ANSWER")).isEqualTo(ReportCategory.WRONG_ANSWER)
        assertThat(ReportCategory.from("QUESTION_ERROR")).isEqualTo(ReportCategory.QUESTION_ERROR)
        assertThat(ReportCategory.from("HINT_ERROR")).isEqualTo(ReportCategory.HINT_ERROR)
        assertThat(ReportCategory.from("OTHER")).isEqualTo(ReportCategory.OTHER)
    }

    @Test
    fun `미지원 값은 예외를 던진다`() {
        assertThatThrownBy { ReportCategory.from("SOMETHING_ELSE") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `대소문자가 다르면 미지원으로 거부된다`() {
        // 코드는 정확히 일치해야 한다(느슨한 매칭은 클라이언트 오타를 조용히 삼킨다).
        assertThatThrownBy { ReportCategory.from("wrong_answer") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
