package watson.bytecs.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import watson.bytecs.ui.theme.BcsColors

/**
 * DESIGN_SYSTEM.md §6 도메인 시각 매핑을 **순수 함수**로 못박은 곳.
 *
 * 색 선택을 컴포저블 안에 숨기지 않고 여기로 끌어낸 이유: 무낙인·비처벌 규칙(§2.2, §6.2)은
 * "이 상태에는 이 토큰을 쓴다/쓰지 않는다"는 **매핑 규칙**이라, 렌더링 없이 그대로 단언할 수 있어야 한다.
 * (컴포즈 UI 테스트로는 실제 칠해진 색을 읽기 어렵다.)
 */

/** 카드·배지 한 벌의 색. [accent]는 좌측 스트라이프·아이콘 같은 액센트에 쓴다. */
@Immutable
internal data class BcsTone(
    val background: Color,
    val accent: Color,
    val content: Color,
)

/**
 * 아무 상태도 주장하지 않는 기본 톤(옅은 표면 + 보조 텍스트).
 *
 * "강조할 게 없다"를 뜻하는 자리에 공통으로 쓴다 — 끊긴 스트릭([streakTone]), 앞쪽 힌트([hintTone]).
 * 원칙 1(색을 아껴 정보가 스스로 위계를 갖게 한다)의 바닥값이라, 여러 곳에서 같은 색이 나오는 건
 * 우연이 아니라 같은 의미다. 그래서 한 함수로 둔다.
 */
internal fun neutralTone(colors: BcsColors): BcsTone = BcsTone(
    background = colors.surfaceSubtle,
    accent = colors.textSecondary,
    content = colors.textSecondary,
)

/**
 * §5.16 StreakBadge 색 매핑.
 *
 * ⚠️ 규칙 두 가지를 여기서 못박는다.
 *  1. 어떤 경우에도 `danger`(Material error 슬롯)를 쓰지 않는다 — 스트릭이 끊긴 건 실패가 아니다.
 *  2. 끊김([days] == 0)에는 `streak`(불꽃) 톤을 쓰지 않는다 — '불이 꺼진다'식 상실 공포 연출 금지(§2.2).
 *     끊김은 중립 톤 + "다시 시작해요" 초대로 표현한다.
 *
 * 본문 색이 `streak`가 아니라 `textPrimary`인 것은 판단이다: `streak`(orange-500)를
 * `streakContainer`(orange-50) 위에 올리면 명암비가 본문 기준에 못 미친다. 불꽃 액센트만 `streak`로 두고
 * 글자는 읽히는 색을 쓴다. (`onStreakContainer` 토큰이 생기면 그것으로 교체한다.)
 */
internal fun streakTone(days: Int, colors: BcsColors): BcsTone = if (days > 0) {
    BcsTone(background = colors.streakContainer, accent = colors.streak, content = colors.textPrimary)
} else {
    neutralTone(colors)
}

/**
 * §6.2 불일치(재시도) 색 매핑 — ⭐️ **이 서비스에서 가장 중요한 규칙**(§2.2).
 *
 * 사용자가 틀렸을 때 빨강·경고 신호가 나오면 원칙 5(무낙인) 위반이고, 그건 "공부 부담을 없앤다"는
 * 존재 이유를 정면으로 거스른다. 오답은 에러가 아니라 '아직'이다.
 *
 * 그래서 불일치는 `neutralNudge` 중립 톤만 쓴다 — danger도, success도, 경고색도 아니다.
 */
internal fun retryTone(colors: BcsColors): BcsTone = BcsTone(
    background = colors.neutralNudgeBackground,
    accent = colors.neutralNudgeForeground,
    content = colors.neutralNudgeForeground,
)

/**
 * §6.2 근접(오탈자) 색 매핑 — info 톤.
 *
 * ⭐️ [retryTone]과 **반드시 구별되는 톤**이다(§5.6). 두 상황에서 사용자가 할 일이 다르기 때문이다:
 * 불일치는 "다시 생각해 보세요"(중립), 근접은 "생각은 맞았고 오타만 보세요"(info). 같은 회색으로
 * 뭉뚱그리면 그 정보가 사라진다. 물론 여기에도 danger는 없다 — 근접도 오답이고, 오답은 처벌 대상이 아니다.
 */
internal fun nearMissTone(colors: BcsColors): BcsTone = BcsTone(
    background = colors.infoContainer,
    accent = colors.info,
    content = colors.onInfoContainer,
)

/**
 * §5.12 시스템 오류 색 매핑(ErrorBanner).
 *
 * ⭐️ 시스템 오류도 danger가 아니다 — danger는 파괴적 행동(계정 삭제) 전용이다(§2.2).
 * 네트워크가 끊긴 건 사용자 잘못이 아니므로 중립 톤으로 안내하고 재시도 경로를 준다.
 */
internal fun systemErrorTone(colors: BcsColors): BcsTone = BcsTone(
    background = colors.surfaceSubtle,
    // [ErrorBanner]는 Column이라 스트라이프가 없어 이 값을 읽지 않는다. [BcsTone] 3필드를 채우려 있는
    // 자리이므로 중립값을 둔다 — 배너에 액센트가 생기면 그때 이 색이 쓰인다.
    accent = colors.textSecondary,
    content = colors.textPrimary,
)

/**
 * §6.1 힌트 위계 색 매핑 — **순서상 위치**로만 강도를 정한다.
 *
 * ⚠️ 고정된 L1/L2 종류 사다리가 아니다. 힌트 개수·종류는 문제마다 다르므로(0~N개), 종류를 보지 않고
 * "몇 번째인가"만 본다. 마지막(가장 뒤) 힌트가 가장 강한 info 톤이고, 그 앞은 모두 옅은 톤이다
 * (스스로 떠올릴 여지를 남긴다).
 *
 * 어떤 위치에도 경고색을 쓰지 않는다 — 힌트는 전부 info 계열이다(§6.1).
 */
internal fun hintTone(index: Int, total: Int, colors: BcsColors): BcsTone {
    val isStrongest = total > 0 && index == total - 1
    return if (isStrongest) {
        BcsTone(background = colors.infoContainer, accent = colors.info, content = colors.onInfoContainer)
    } else {
        neutralTone(colors)
    }
}
