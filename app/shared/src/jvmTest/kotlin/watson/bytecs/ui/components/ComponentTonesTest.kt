package watson.bytecs.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import watson.bytecs.ui.theme.BcsColors
import watson.bytecs.ui.theme.BcsDarkColorScheme
import watson.bytecs.ui.theme.BcsDarkColors
import watson.bytecs.ui.theme.BcsLightColorScheme
import watson.bytecs.ui.theme.BcsLightColors

/**
 * DESIGN_SYSTEM.md §2.2 · §6 도메인 색 규칙을 못박는 테스트.
 *
 * 이 규칙들은 "화면이 예뻐 보이는가"가 아니라 **서비스의 존재 이유**(무낙인·비처벌)라서, 렌더링 없이
 * 매핑 함수 수준에서 직접 단언한다. 라이트/다크 두 스킴 모두 검사한다.
 */
class ComponentTonesTest {

    /** 라이트/다크 각각의 (브랜드 토큰, 그 스킴의 danger) 쌍. */
    private val schemes: List<Pair<BcsColors, Color>> = listOf(
        BcsLightColors to BcsLightColorScheme.error,
        BcsDarkColors to BcsDarkColorScheme.error,
    )

    /** 해당 스킴에서 '처벌 신호'로 취급되는 색 전부(전경·옅은 배경 모두). */
    private fun punitiveColors(scheme: ColorScheme): List<Color> =
        listOf(scheme.error, scheme.errorContainer, scheme.onErrorContainer)

    /** 라이트/다크 각각의 (브랜드 토큰, Material 스킴) 쌍. */
    private val schemePairs: List<Pair<BcsColors, ColorScheme>> = listOf(
        BcsLightColors to BcsLightColorScheme,
        BcsDarkColors to BcsDarkColorScheme,
    )

    private val schemesWithPunitive = listOf(
        BcsLightColors to punitiveColors(BcsLightColorScheme),
        BcsDarkColors to punitiveColors(BcsDarkColorScheme),
    )

    // ── §2.2 "가장 중요한 규칙" — 오답에 처벌 신호 금지 ──────────────────────────

    /**
     * ⭐️⭐️ DESIGN_SYSTEM §2.2가 "가장 중요한 규칙"으로 명시한 것:
     * 사용자가 틀렸을 때 빨강이 나오면 원칙 5(무낙인) 위반이고, 그건 이 서비스의 존재 이유를 거스른다.
     *
     * 불일치·근접 **어느 쪽도** danger 계열(error/errorContainer/onErrorContainer)을 쓰지 않는다.
     */
    @Test
    fun 오답_피드백은_불일치든_근접이든_어떤_처벌색도_쓰지_않는다() {
        for ((colors, punitive) in schemesWithPunitive) {
            for ((name, tone) in listOf("불일치" to retryTone(colors), "근접" to nearMissTone(colors))) {
                for (bad in punitive) {
                    assertNotEquals(bad, tone.background, "$name 배경에 처벌색")
                    assertNotEquals(bad, tone.accent, "$name 액센트에 처벌색")
                    assertNotEquals(bad, tone.content, "$name 본문에 처벌색")
                }
            }
        }
    }

    /** 불일치는 중립(neutralNudge) 톤이다 — 회색으로 "아직이에요"를 말한다. */
    @Test
    fun 불일치는_중립_넛지_톤을_쓴다() {
        for ((colors, _) in schemes) {
            val tone = retryTone(colors)
            assertEquals(colors.neutralNudgeBackground, tone.background)
            assertEquals(colors.neutralNudgeForeground, tone.accent)
            assertEquals(colors.neutralNudgeForeground, tone.content)
        }
    }

    /** 근접은 info 톤이다 — "생각은 맞았고 오타만 보세요"는 안내지 경고가 아니다. */
    @Test
    fun 근접은_info_톤을_쓴다() {
        for ((colors, _) in schemes) {
            val tone = nearMissTone(colors)
            assertEquals(colors.infoContainer, tone.background)
            assertEquals(colors.info, tone.accent)
            assertEquals(colors.onInfoContainer, tone.content)
        }
    }

    /**
     * ⭐️ §5.6: 근접은 불일치와 **구별되는 톤**이어야 한다.
     * 둘이 같은 색으로 수렴하면 "오타 때문임"이라는 정보가 사라진다.
     */
    @Test
    fun 근접은_불일치와_다른_톤이다() {
        for ((colors, _) in schemes) {
            assertNotEquals(retryTone(colors).background, nearMissTone(colors).background)
            assertNotEquals(retryTone(colors).accent, nearMissTone(colors).accent)
        }
    }

    /** ⭐️ 정답 긍정(success)을 오답에 쓰지 않는다 — 뒤집힌 신호도 정직하지 않다(§2.2). */
    @Test
    fun 오답_피드백은_정답_긍정색을_쓰지_않는다() {
        for ((colors, _) in schemes) {
            for (tone in listOf(retryTone(colors), nearMissTone(colors))) {
                assertNotEquals(colors.success, tone.background)
                assertNotEquals(colors.success, tone.accent)
                assertNotEquals(colors.successContainer, tone.background)
            }
        }
    }

    /** ⭐️ §5.12: 시스템 오류도 danger가 아니다 — danger는 계정 삭제 전용이다. */
    @Test
    fun 시스템_오류_배너도_처벌색을_쓰지_않는다() {
        for ((colors, punitive) in schemesWithPunitive) {
            val tone = systemErrorTone(colors)
            for (bad in punitive) {
                assertNotEquals(bad, tone.background)
                assertNotEquals(bad, tone.accent)
                assertNotEquals(bad, tone.content)
            }
        }
    }

    // ── §5.1 · §5.13 PrimaryButton 역할 ──────────────────────────────────────

    /**
     * ⭐️ 기본 주요 버튼('정답 확인하기')에는 어떤 처벌색도 섞이지 않는다.
     * 화면에서 가장 많이 쓰이는 버튼이라, 여기가 뚫리면 오답 순간에 빨강이 뜨는 길이 열린다.
     */
    @Test
    fun 기본_주요_버튼은_어떤_처벌색도_쓰지_않는다() {
        for ((colors, scheme) in schemePairs) {
            val tone = primaryButtonTone(PrimaryButtonRole.Default, colors, scheme)
            for (bad in punitiveColors(scheme)) {
                assertNotEquals(bad, tone.container, "기본 버튼 container에 처벌색")
                assertNotEquals(bad, tone.containerPressed, "기본 버튼 눌림에 처벌색")
                assertNotEquals(bad, tone.content, "기본 버튼 본문에 처벌색")
            }
        }
    }

    /** 기본 주요 버튼은 브랜드 primary를 쓴다(§5.1). 눌림은 primaryPressed 토큰. */
    @Test
    fun 기본_주요_버튼은_브랜드_primary를_쓴다() {
        for ((colors, scheme) in schemePairs) {
            val tone = primaryButtonTone(PrimaryButtonRole.Default, colors, scheme)
            assertEquals(scheme.primary, tone.container)
            assertEquals(colors.primaryPressed, tone.containerPressed)
            assertEquals(scheme.onPrimary, tone.content)
        }
    }

    /** §5.13: 파괴적 버튼(계정 삭제)은 danger를 쓴다 — danger가 등장해야 하는 유일한 자리다. */
    @Test
    fun 파괴적_버튼은_danger를_쓴다() {
        for ((colors, scheme) in schemePairs) {
            val tone = primaryButtonTone(PrimaryButtonRole.Destructive, colors, scheme)
            assertEquals(scheme.error, tone.container)
            assertEquals(scheme.onError, tone.content)
            // 눌림에도 container를 유지한다(dangerPressed 토큰이 없고, 눌림은 스케일이 담당).
            assertEquals(scheme.error, tone.containerPressed)
        }
    }

    /**
     * ⭐️⭐️ §2.2 "danger 색은 계정 삭제에서만 등장"을 역할 전수로 못박는다.
     *
     * 앞으로 역할이 늘어도 danger를 쓰는 건 파괴적 행동 **하나뿐**이어야 한다.
     * 새 역할에 danger를 실으면 여기서 깨진다 — 그 순간이 "정말 필요한가"를 묻는 관문이다.
     */
    @Test
    fun danger를_쓰는_역할은_파괴적_행동_하나뿐이다() {
        for ((colors, scheme) in schemePairs) {
            val rolesUsingDanger = PrimaryButtonRole.entries.filter { role ->
                val tone = primaryButtonTone(role, colors, scheme)
                punitiveColors(scheme).any { it == tone.container || it == tone.containerPressed }
            }
            assertEquals(listOf(PrimaryButtonRole.Destructive), rolesUsingDanger)
        }
    }

    /** 기본과 파괴적은 반드시 구별된다 — 삭제 버튼이 평범한 버튼처럼 보이면 §5.13의 경고 기능이 사라진다. */
    @Test
    fun 파괴적_버튼은_기본_버튼과_다른_색이다() {
        for ((colors, scheme) in schemePairs) {
            val default = primaryButtonTone(PrimaryButtonRole.Default, colors, scheme)
            val destructive = primaryButtonTone(PrimaryButtonRole.Destructive, colors, scheme)
            assertNotEquals(default.container, destructive.container)
        }
    }

    // ── §5.16 StreakBadge ────────────────────────────────────────────────────

    /** ⭐️ 스트릭이 끊겨도 실패가 아니다 — 어떤 일수에도 danger가 새어 들어오면 안 된다. */
    @Test
    fun 스트릭_배지는_끊김을_포함한_어떤_상태에도_danger를_쓰지_않는다() {
        for ((colors, danger) in schemes) {
            for (days in listOf(0, 1, 7, 365)) {
                val tone = streakTone(days, colors)
                assertNotEquals(danger, tone.background, "days=$days 배경에 danger")
                assertNotEquals(danger, tone.accent, "days=$days 액센트에 danger")
                assertNotEquals(danger, tone.content, "days=$days 본문에 danger")
            }
        }
    }

    /**
     * ⭐️ 끊김(0일)에 불꽃(streak) 톤을 쓰지 않는다 — '불이 꺼졌다'는 상실 공포 연출 금지(§2.2).
     * 끊김은 중립 톤으로 "다시 시작해요" 초대여야 한다.
     */
    @Test
    fun 스트릭이_끊기면_streak_토큰_대신_중립_톤을_쓴다() {
        for ((colors, _) in schemes) {
            val broken = streakTone(0, colors)
            assertNotEquals(colors.streak, broken.background)
            assertNotEquals(colors.streak, broken.accent)
            assertNotEquals(colors.streakContainer, broken.background)
            assertEquals(colors.surfaceSubtle, broken.background)
            assertEquals(colors.textSecondary, broken.content)
        }
    }

    /** 살아 있는 스트릭에는 streak 토큰을 쓴다(§5.16 "상승은 streak(불꽃) 톤"). */
    @Test
    fun 스트릭이_이어지면_streak_토큰을_쓴다() {
        for ((colors, _) in schemes) {
            val alive = streakTone(3, colors)
            assertEquals(colors.streakContainer, alive.background)
            assertEquals(colors.streak, alive.accent)
        }
    }

    // ── §6.1 힌트 위계 ────────────────────────────────────────────────────────

    /** ⭐️ 힌트는 전부 info 계열이다 — 어떤 위치의 힌트에도 경고색이 붙지 않는다. */
    @Test
    fun 힌트_톤은_어떤_위치에도_danger를_쓰지_않는다() {
        for ((colors, danger) in schemes) {
            for (total in 1..5) {
                for (index in 0 until total) {
                    val tone = hintTone(index, total, colors)
                    assertNotEquals(danger, tone.background, "index=$index/total=$total 배경에 danger")
                    assertNotEquals(danger, tone.accent, "index=$index/total=$total 액센트에 danger")
                    assertNotEquals(danger, tone.content, "index=$index/total=$total 본문에 danger")
                }
            }
        }
    }

    /**
     * ⭐️ 고정 L1/L2 사다리 금지 — 강도는 **순서상 위치**로만 정해진다.
     * 즉 같은 index라도 전체 개수가 달라지면 톤이 달라진다: 3개짜리의 1번은 옅은 힌트지만,
     * 2개짜리의 1번은 마지막이라 가장 강한 info 톤이다.
     */
    @Test
    fun 힌트_강도는_종류가_아니라_전체_개수_속_위치로_정해진다() {
        val colors = BcsLightColors
        assertEquals(colors.surfaceSubtle, hintTone(index = 1, total = 3, colors = colors).background)
        assertEquals(colors.infoContainer, hintTone(index = 1, total = 2, colors = colors).background)
    }

    /** 마지막 힌트가 가장 강한 info 톤. 힌트가 하나뿐인 문제도 그 하나가 마지막이다(개수 가정 없음). */
    @Test
    fun 마지막_힌트가_가장_강한_info_톤이다() {
        for ((colors, _) in schemes) {
            for (total in 1..5) {
                val last = hintTone(total - 1, total, colors)
                assertEquals(colors.infoContainer, last.background, "total=$total")
                assertEquals(colors.onInfoContainer, last.content, "total=$total")

                // 앞쪽 힌트는 전부 옅은 톤(스스로 떠올릴 여지를 남긴다).
                for (index in 0 until total - 1) {
                    assertEquals(colors.surfaceSubtle, hintTone(index, total, colors).background, "index=$index")
                }
            }
        }
    }

    /** info는 primary 계열과 같은 톤이어야 한다(§2.2) — 힌트가 경고로 보이지 않는 근거. */
    @Test
    fun info_토큰은_primary와_같은_색이다() {
        assertEquals(BcsLightColorScheme.primary, BcsLightColors.info)
        assertEquals(BcsDarkColorScheme.primary, BcsDarkColors.info)
    }

    // ── §5.9 난이도 ───────────────────────────────────────────────────────────

    @Test
    fun 난이도는_아는_값만_라벨로_바꾸고_모르는_값은_표시하지_않는다() {
        assertEquals("쉬움", difficultyLabel("EASY"))
        assertEquals("보통", difficultyLabel("medium"))
        assertEquals("어려움", difficultyLabel("Hard"))
        assertTrue(difficultyLabel(null) == null)
        assertTrue(difficultyLabel("") == null)
        assertTrue(difficultyLabel("IMPOSSIBLE") == null)
    }
}
