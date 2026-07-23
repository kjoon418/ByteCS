package watson.bytecs.interview.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** 면접 준비도의 상태 파생(전 포인트 충족→검증됨, 일부→부분, 0개→미검증)과 재도전 시 최신 결과로 덮어쓰는지를 검증한다(DI4). */
class InterviewReadinessTest {

    private companion object {
        val UPDATED_AT: Instant = Instant.parse("2026-07-23T00:00:00Z")
    }

    @Test
    fun `전 포인트를 충족하면 검증됨이다`() {
        val readiness = InterviewReadiness.initial(1L, 10L)

        readiness.applyResult(satisfiedCount = 3, totalCount = 3, updatedAt = UPDATED_AT)

        assertThat(readiness.status).isEqualTo(InterviewReadinessStatus.VERIFIED)
        assertThat(readiness.isBelowVerified).isFalse()
    }

    @Test
    fun `일부만 충족하면 부분이다`() {
        val readiness = InterviewReadiness.initial(1L, 10L)

        readiness.applyResult(satisfiedCount = 1, totalCount = 3, updatedAt = UPDATED_AT)

        assertThat(readiness.status).isEqualTo(InterviewReadinessStatus.PARTIAL)
        assertThat(readiness.isBelowVerified).isTrue()
    }

    @Test
    fun `0개 충족이면 미검증이다`() {
        val readiness = InterviewReadiness.initial(1L, 10L)

        readiness.applyResult(satisfiedCount = 0, totalCount = 3, updatedAt = UPDATED_AT)

        assertThat(readiness.status).isEqualTo(InterviewReadinessStatus.UNVERIFIED)
    }

    @Test
    fun `재도전 결과는 이전 결과를 덮어쓴다`() {
        val readiness = InterviewReadiness.initial(1L, 10L)
        readiness.applyResult(satisfiedCount = 3, totalCount = 3, updatedAt = UPDATED_AT)

        readiness.applyResult(satisfiedCount = 0, totalCount = 3, updatedAt = UPDATED_AT.plusSeconds(60))

        assertThat(readiness.status).isEqualTo(InterviewReadinessStatus.UNVERIFIED)
    }
}
