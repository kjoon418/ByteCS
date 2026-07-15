package watson.bytecs.ui.components

import androidx.compose.material3.ColorScheme
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

/** 버튼 한 벌의 색. 눌림 색이 따로 있어 카드·배지의 [BcsTone]과 슬롯이 다르다. */
@Immutable
internal data class ButtonTone(
    val container: Color,
    val containerPressed: Color,
    val content: Color,
)

/**
 * §5.1 PrimaryButton의 **역할**. 색이 아니라 의도를 고른다.
 *
 * ⭐️ 색(`containerColor: Color`)을 직접 받지 않는 게 핵심이다. §2.2는 danger를 "파괴적 행동에만,
 * 계정 삭제에서만 등장"으로 못박는데, 임의 색을 받는 순간 그 규칙은 검증도 감사도 불가능해진다
 * (오답 제출 버튼을 빨갛게 만드는 데 한 줄이면 충분해진다).
 *
 * 역할로 받으면 두 가지를 얻는다.
 *  1. 색 결정이 [primaryButtonTone] 한 곳을 지나므로 테스트로 못박을 수 있다.
 *  2. `role = Destructive`를 grep하면 **danger 사용처 전수**가 나온다 — "여기서만 등장"이 감사 가능해진다.
 *
 * 다른 색이 필요해지면 항목을 늘려야 하는데, 그 불편함이 곧 "정말 필요한가"를 묻는 관문이다.
 */
enum class PrimaryButtonRole {
    /** 기본 — 브랜드 primary. 화면의 유일한 강조 액션(정답 확인하기 등). */
    Default,

    /** ⚠️ 되돌릴 수 없는 파괴적 행동(계정 삭제, §5.13). **이 서비스에서 danger가 등장하는 유일한 자리.** */
    Destructive,
}

/**
 * §5.1 · §5.13 PrimaryButton 역할별 색 매핑.
 *
 * ⚠️ [PrimaryButtonRole.Default]에는 어떤 처벌색도 섞이지 않고, danger는 오직
 * [PrimaryButtonRole.Destructive]에만 나온다. 두 규칙 모두 테스트로 못박혀 있다.
 *
 * 파괴적 역할의 눌림 색이 container와 같은 건 판단이다: `dangerPressed` 토큰이 없고, 눌림 피드백은
 * 이미 `pressScaleStrong` 스케일이 담당한다. 없는 토큰을 있는 척 만들지 않는다.
 */
internal fun primaryButtonTone(
    role: PrimaryButtonRole,
    colors: BcsColors,
    colorScheme: ColorScheme,
): ButtonTone = when (role) {
    PrimaryButtonRole.Default -> ButtonTone(
        container = colorScheme.primary,
        containerPressed = colors.primaryPressed, // §2.1 실제 primaryPressed 토큰
        content = colorScheme.onPrimary,
    )

    PrimaryButtonRole.Destructive -> ButtonTone(
        container = colorScheme.error,
        containerPressed = colorScheme.error,
        content = colorScheme.onError,
    )
}

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
