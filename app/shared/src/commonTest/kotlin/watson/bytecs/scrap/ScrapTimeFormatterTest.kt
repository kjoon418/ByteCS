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

    // ── KST 보정(2026-07-16 오너 결정) ───────────────────────────────────────
    // UTC 그대로 날짜를 계산하면 KST 자정~오전 9시에 스크랩한 건이 "전날"로 잘못 표시된다.
    // 달력 날짜는 KST(UTC+9) 기준으로 계산해야 한다.

    /** ⚠️ 가드레일: KST 새벽(UTC로는 전날 저녁)에 스크랩하면 UTC 날짜가 아니라 KST 날짜로 표기한다. */
    @Test
    fun KST_새벽에_스크랩한_건은_UTC_전날이_아니라_KST_당일로_표기한다() {
        // scrappedAt = UTC 2026-07-08T18:30:00 = KST 2026-07-09T03:30:00
        val scrappedAt = Instant.parse("2026-07-08T18:30:00Z")
        val eightDaysLater = scrappedAt + 8.days

        assertEquals("7월 9일", formatScrappedAt(scrappedAt, eightDaysLater))
    }

    /** ⚠️ 가드레일: KST 자정 직전(UTC 14:59:59)과 직후(UTC 15:00:00)는 KST 날짜가 하루 갈린다. */
    @Test
    fun KST_자정_경계_전후로_달력_날짜가_하루_갈린다() {
        val justBeforeKstMidnight = Instant.parse("2026-07-01T14:59:59Z") // KST 2026-07-01T23:59:59
        val justAfterKstMidnight = Instant.parse("2026-07-01T15:00:00Z") // KST 2026-07-02T00:00:00
        val now = Instant.parse("2026-07-10T00:00:00Z")

        assertEquals("7월 1일", formatScrappedAt(justBeforeKstMidnight, now))
        assertEquals("7월 2일", formatScrappedAt(justAfterKstMidnight, now))
    }

    /** 연 경계도 KST 기준 — UTC로는 작년이어도 KST로 올해면 연도를 생략한다. */
    @Test
    fun 연_경계는_KST_기준으로_판정한다_같은_해면_연도_생략() {
        // scrappedAt = UTC 2025-12-31T16:00:00 = KST 2026-01-01T01:00:00
        val scrappedAt = Instant.parse("2025-12-31T16:00:00Z")
        val lateJanuary = Instant.parse("2026-01-25T00:00:00Z")

        assertEquals("1월 1일", formatScrappedAt(scrappedAt, lateJanuary))
    }

    /** 반대로 KST 기준으로도 작년이면 연도를 함께 표기한다. */
    @Test
    fun 연_경계는_KST_기준으로_판정한다_KST로도_작년이면_연도_표기() {
        // scrappedAt = UTC 2025-12-31T10:00:00 = KST 2025-12-31T19:00:00 (여전히 작년)
        val scrappedAt = Instant.parse("2025-12-31T10:00:00Z")
        val lateJanuary = Instant.parse("2026-01-25T00:00:00Z")

        assertEquals("2025년 12월 31일", formatScrappedAt(scrappedAt, lateJanuary))
    }

    /**
     * ⚠️ 가드레일: `now` 쪽도 KST 보정이 빠지면 안 된다 — `now`가 KST 자정 직후(UTC로는 여전히
     * 작년 저녁)일 때, `now`에 보정을 빼먹으면 "올해"가 아니라 "작년"으로 잘못 분류되어 연도가
     * 누락된다.
     */
    @Test
    fun 연_경계는_now_쪽도_KST_보정이_필요하다() {
        // scrappedAt = UTC 2025-12-01T00:00:00 = KST 2025-12-01T09:00:00 (명백히 작년)
        val scrappedAt = Instant.parse("2025-12-01T00:00:00Z")
        // now = UTC 2025-12-31T16:00:00 = KST 2026-01-01T01:00:00 (이미 올해)
        val now = Instant.parse("2025-12-31T16:00:00Z")

        assertEquals("2025년 12월 1일", formatScrappedAt(scrappedAt, now))
    }
}
