package watson.bytecs.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException

/**
 * 무결성 위반·낙관적 락 실패가 도메인 의미를 단정하지 않는 중립 CONFLICT(409)로 매핑되는지 고정한다.
 * (이메일 중복 같은 의미 부여는 각 서비스가 책임진다 — 전역 DIV 핸들러는 더 이상 EMAIL_DUPLICATED로 오매핑하지 않는다.)
 */
class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `무결성 위반은 중립 CONFLICT 409로 매핑된다`() {
        val response = handler.handleDataIntegrityViolation(DataIntegrityViolationException("some constraint"))

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.errorCode).isEqualTo(ErrorCode.CONFLICT)
    }

    @Test
    fun `낙관적 락 실패는 CONFLICT 409로 매핑된다`() {
        val response = handler.handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException("stale version", null),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.errorCode).isEqualTo(ErrorCode.CONFLICT)
    }
}
