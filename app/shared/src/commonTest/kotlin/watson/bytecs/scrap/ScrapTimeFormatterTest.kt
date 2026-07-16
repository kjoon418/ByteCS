package watson.bytecs.scrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [formatScrappedAt] 임계값 경계 테스트. [ScrapTimeFormatter.kt]의 KDoc에 근거를 남겼다 —
 * 7일 미만은 상대 시간(신선도 신호), 7일 이상은 달력 날짜(역산 비용 방지)가 더 친절하다.
 *
 * 순수 함수라 [now]를 고정해 결정적으로 검증한다(실제 화면의 [kotlin.time.Clock.System] 의존 없이).
 */
@OptIn(ExperimentalTime::class)
class ScrapTimeFormatterTest {

    private val now = Instant.parse("2026-07-15T12:00:00Z")

    @Test
    fun 일_분_미만은_방금_전이다() {
        assertEquals("방금 전", formatScrappedAt(now - 30.seconds, now))
    }

    // ── 59분/60분 경계 ─────────────────────────────────────────────────────

    @Test
    fun 오십구_분_전은_분_단위로_표기한다() {
        assertEquals("59분 전", formatScrappedAt(now - 59.minutes, now))
    }

    @Test
    fun 육십_분_전은_한_시간_전으로_넘어간다() {
        assertEquals("1시간 전", formatScrappedAt(now - 60.minutes, now))
    }

    // ── 23시간/24시간 경계 ─────────────────────────────────────────────────

    @Test
    fun 스물세_시간_전은_시간_단위로_표기한다() {
        assertEquals("23시간 전", formatScrappedAt(now - 23.hours, now))
    }

    @Test
    fun 스물네_시간_전은_하루_전으로_넘어간다() {
        assertEquals("1일 전", formatScrappedAt(now - 24.hours, now))
    }

    // ── 6일/7일 경계 ──────────────────────────────────────────────────────

    @Test
    fun 육일_전은_일_단위로_표기한다() {
        assertEquals("6일 전", formatScrappedAt(now - 6.days, now))
    }

    /** ⚠️ 가드레일: 7일이 지나면 상대 표현("7일 전")이 아니라 달력 날짜로 넘어간다. */
    @Test
    fun 칠일_전은_상대_시간이_아니라_달력_날짜로_넘어간다() {
        assertEquals("7월 8일", formatScrappedAt(now - 7.days, now))
    }

    // ── 연 경계 ───────────────────────────────────────────────────────────

    /** 같은 해라면 연도를 덧붙이지 않는다(불필요한 정보로 소음을 늘리지 않는다). */
    @Test
    fun 같은_해의_오래된_스크랩은_연도_없이_월일만_표기한다() {
        val recentNow = Instant.parse("2026-07-15T00:00:00Z")
        val scrappedAt = Instant.parse("2026-01-03T00:00:00Z")

        assertEquals("1월 3일", formatScrappedAt(scrappedAt, recentNow))
    }

    /** ⚠️ 가드레일: 해가 다르면 "M월 D일"만으로는 어느 해인지 모호하므로 연도를 함께 밝힌다. */
    @Test
    fun 해가_다르면_연도를_함께_표기한다() {
        val yearBoundaryNow = Instant.parse("2026-01-03T00:00:00Z")
        val scrappedAt = Instant.parse("2025-12-20T00:00:00Z")

        assertEquals("2025년 12월 20일", formatScrappedAt(scrappedAt, yearBoundaryNow))
    }
}
