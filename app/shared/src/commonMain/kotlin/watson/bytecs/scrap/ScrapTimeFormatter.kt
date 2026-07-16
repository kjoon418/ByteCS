package watson.bytecs.scrap

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 스크랩 저장 시각을 사람이 읽기 쉬운 형태로 표기한다(§B-4, 2026-07-16 오너 결정).
 *
 * ⭐️ 임계 근거(왜 7일인가): 스크랩 목록의 시각은 "얼마나 최근에 담았나"라는 신선도 신호다. 담근 지
 * 얼마 안 됐으면 "3시간 전"처럼 상대 표현이 신선도를 한눈에 전달하지만, 일주일이 넘어가면 상대 표현
 * ("9일 전")은 오히려 머릿속에서 오늘 날짜와 역산해야 해 불친절해진다 — 이때는 달력 날짜("7월 5일")가
 * 더 정확하고 읽는 비용도 싸다. 7일(1주)은 이 앱의 학습 리듬이 주 단위이기도 해 자연스러운 경계다.
 *
 * ⭐️ 순수 함수다 — [now]를 주입받아 결정적으로 테스트한다(실제 화면은 [Clock.System]을 기본값으로 쓴다).
 *
 * @param scrappedAt 스크랩한 시각.
 * @param now 기준 시각(테스트에서 고정). 기본은 현재 시각.
 */
@OptIn(ExperimentalTime::class)
fun formatScrappedAt(scrappedAt: Instant, now: Instant = Clock.System.now()): String {
    val elapsed = now - scrappedAt
    return when {
        elapsed < 1.minutes -> "방금 전"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}분 전"
        elapsed < 24.hours -> "${elapsed.inWholeHours}시간 전"
        elapsed < 7.days -> "${elapsed.inWholeDays}일 전"
        else -> formatCalendarDate(scrappedAt, now)
    }
}

/** 7일 이상 지났을 때의 표기 — 달력 날짜. 해가 다르면 연도를 함께 밝힌다. */
@OptIn(ExperimentalTime::class)
private fun formatCalendarDate(scrappedAt: Instant, now: Instant): String {
    val (scrappedYear, scrappedMonth, scrappedDay) = civilFromEpochDay(scrappedAt.epochSeconds.floorDiv(SECONDS_PER_DAY))
    val (nowYear, _, _) = civilFromEpochDay(now.epochSeconds.floorDiv(SECONDS_PER_DAY))
    return if (scrappedYear != nowYear) {
        "${scrappedYear}년 ${scrappedMonth}월 ${scrappedDay}일"
    } else {
        "${scrappedMonth}월 ${scrappedDay}일"
    }
}

private const val SECONDS_PER_DAY = 86_400L

/**
 * 1970-01-01 기준 경과 일수를 (연, 월, 일)로 변환한다(UTC, 프로렙틱 그레고리력).
 *
 * kotlinx-datetime 등 시간대 라이브러리 없이 kotlin-stdlib의 [kotlin.time.Instant]만으로 달력 날짜를
 * 구해야 해서, 잘 알려진 정수 연산 알고리즘(Howard Hinnant, `civil_from_days`,
 * http://howardhinnant.github.io/date_algorithms.html)을 그대로 옮긴다. 부동소수점·라이브러리 의존 없이
 * 모든 KMP 타깃(JVM·Android·Wasm)에서 동일하게 동작한다.
 */
private fun civilFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097 // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // [0, 399]
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0, 365]
    val mp = (5 * doy + 2) / 153 // [0, 11]
    val day = doy - (153 * mp + 2) / 5 + 1 // [1, 31]
    val month = if (mp < 10) mp + 3 else mp - 9 // [1, 12]
    val year = if (month <= 2) y + 1 else y
    return Triple(year.toInt(), month.toInt(), day.toInt())
}
