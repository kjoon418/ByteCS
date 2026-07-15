package watson.bytecs.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsCard
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.StreakBadge
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 02 홈('오늘의 한입'). 재방문 사용자의 기본 진입점 — 오늘의 한입을 시작하거나 이어서 한다.
 *
 * ⭐️ 가벼운 초대·무낙인: "오늘도 한입 해볼까요?" 톤, 분량 기반 진행(타이머 없음), 완료는 긍정 빈 상태,
 * 스트릭은 긍정 동기(끊겨도 죄책감 연출 금지). 가입은 권유로만(막지 않기).
 *
 * ⚠️ 사용자 대면 카피에 '세션'을 쓰지 않는다 — 시스템 용어다. 화면에도, 스크린리더 문구에도 '오늘의 한입'.
 *
 * @param onStartOrContinue 시작/이어서 풀기 → 03 세션 풀이.
 * @param onExtraPractice 오늘 완료 후 '조금 더 풀어보기' → 추가 연습(세션 밖).
 * @param onOpenAccount 계정·설정(06).
 * @param onUpgrade 게스트 가입 유도 → 05.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onOpenAccount: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입(앱 재실행·세션 완료 후 복귀)마다 오늘 상태를 새로 반영한다.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    HomeScreenContent(
        state = state,
        onStartOrContinue = onStartOrContinue,
        onExtraPractice = onExtraPractice,
        onOpenAccount = onOpenAccount,
        onUpgrade = onUpgrade,
        onRetry = viewModel::refresh,
        modifier = modifier,
    )
}

/**
 * 상태 → 화면. 뷰모델을 끼우지 않고 상태만 주입해 그릴 수 있어야 하므로 `internal`이다
 * (게스트/가입자 배타성·스트릭 끊김 톤 같은 도메인 규칙을 상태별로 단언하려면 이 경계가 필요하다).
 */
@Composable
internal fun HomeScreenContent(
    state: HomeUiState,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onOpenAccount: () -> Unit,
    onUpgrade: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = state as? HomeUiState.Ready

    BcsScaffold(
        modifier = modifier,
        topBar = {
            // 하단 탭 바가 없는 화면이므로(§5.15 모바일 우선), 계정 진입점은 헤더 아바타 하나뿐이다.
            HomeHeaderBar(
                dateLabel = ready?.session?.sessionDate?.let(::formatSessionDate),
                isMember = ready?.isMember == true,
                onOpenAccount = onOpenAccount,
            )
        },
        bottomBar = {
            // 주요 액션은 엄지 영역 하단 고정(00 §3.1). 상태에 따라 시작/이어서/추가연습으로 전환.
            ready?.let {
                HomeCtaBar {
                    when {
                        it.isCompleted -> GhostButton(text = "조금 더 풀어보기", onClick = onExtraPractice)
                        it.isInProgress -> PrimaryButton(text = "학습 이어서 하기", onClick = onStartOrContinue)
                        else -> PrimaryButton(text = "오늘의 한입 시작하기", onClick = onStartOrContinue)
                    }
                }
            }
        },
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                HomeUiState.Loading -> HomeSkeleton()
                HomeUiState.Error -> HomeError(onRetry = onRetry)
                is HomeUiState.Ready -> HomeReady(state = state, onUpgrade = onUpgrade)
            }
        }
    }
}

/** 서버의 `2026-05-14` → 화면의 `2026.05.14`. 날짜감만 주는 보조 정보라 형식 하나로 족하다. */
private fun formatSessionDate(sessionDate: String): String = sessionDate.replace('-', '.')

/** 헤더: 날짜(보조) + 계정 진입점. 날짜는 오늘 상태를 불러오기 전엔 비운다. */
@Composable
private fun HomeHeaderBar(
    dateLabel: String?,
    isMember: Boolean,
    onOpenAccount: () -> Unit,
) {
    val colors = LocalBcsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dateLabel.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        AccountEntry(isMember = isMember, onClick = onOpenAccount)
    }
}

/**
 * 계정 진입점(§5.15) — 게스트/가입자 **배타적** 표현.
 *
 * 게스트는 중립 아이콘(가입 안 했다는 낙인이 아니라 그냥 '계정 없음'), 가입자는 채워진 프로필 원형.
 * ⚠️ 게스트라고 빨강·경고를 붙이지 않는다. 가입 권유는 본문 배너([GuestUpgradeBanner])가 맡는다.
 *
 * 프로필 사진은 아직 상태에 없어(회원 여부만 안다) 글리프로 대신한다.
 */
@Composable
private fun AccountEntry(
    isMember: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalBcsColors.current
    val description = if (isMember) "내 계정·설정 열기" else "계정 만들기·설정 열기"
    Box(
        modifier = Modifier
            // 원형 자체를 최소 터치 타깃 크기로 그린다(§7) — 작은 아이콘을 정확히 누를 수 있게.
            .size(BcsDimens.minTouchTarget)
            .clip(CircleShape)
            .background(if (isMember) colors.infoContainer else colors.surfaceSubtle)
            .border(BcsDimens.borderWidth, colors.borderSubtle, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "👤",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isMember) colors.onInfoContainer else colors.textTertiary,
        )
    }
}

/** 하단 고정 CTA 바 — surface 위에 얹고 상단 1dp 경계로 콘텐츠와 분리한다(§4.3 다크는 그림자 대신 경계). */
@Composable
private fun HomeCtaBar(content: @Composable () -> Unit) {
    val colors = LocalBcsColors.current
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BcsDimens.borderWidth)
                .background(colors.borderSubtle),
        )
        Box(modifier = Modifier.padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4)) {
            content()
        }
    }
}

@Composable
private fun HomeReady(
    state: HomeUiState.Ready,
    onUpgrade: () -> Unit,
) {
    val session = state.session

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 글자 확대·작은 화면에서도 하단 CTA에 가려 잘리지 않도록 본문은 스스로 스크롤한다(§7).
            .verticalScroll(rememberScrollState())
            .padding(horizontal = BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space5),
    ) {
        Spacer(Modifier.height(BcsDimens.space2))

        Greeting()

        // 스트릭 — §5.16 공용 배지. 끊겨도(0일) 죄책감·상실 공포 대신 "다시 시작해요" 톤이고,
        // 색 규칙(danger 금지·끊김에 streak 톤 금지)은 streakTone에 못박혀 있다.
        session.streak?.let { streak ->
            StreakBadge(days = streak.count)
        }

        TodayBiteCard(state = state)

        // 오늘 완료 — 긍정 빈 상태(§5.10). 액션['조금 더 풀어보기']은 하단 CTA에 있다.
        if (state.isCompleted) {
            CompletedCard()
        }

        // 게스트 가입 유도(은은·권유). 막지 않는다. 가입자에겐 아예 나오지 않는다(배타적).
        if (!state.isMember) {
            GuestUpgradeBanner(onUpgrade = onUpgrade)
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/** 인사 — 가벼운 초대 톤. '한입'만 primary로 집어 이 서비스의 단위를 각인한다. */
@Composable
private fun Greeting() {
    val colors = LocalBcsColors.current
    val text = buildAnnotatedString {
        append("안녕하세요!\n오늘도 ")
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("한입") }
        append(" 해볼까요?")
    }
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        color = colors.textPrimary,
    )
}

/**
 * 오늘의 한입 카드 — 배지 + 상태 제목 + 분량 진행(`2 / 10`).
 * ⭐️ 분량 기반이다. 카운트다운 타이머가 아니다(§5.4).
 */
@Composable
private fun TodayBiteCard(state: HomeUiState.Ready) {
    val colors = LocalBcsColors.current
    val session = state.session
    val title = when {
        state.isCompleted -> "오늘 몫 완료"
        state.isInProgress -> "이어서 풀기"
        else -> "지금 시작하기"
    }

    BcsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
                TodayBiteBadge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${session.solvedCount}",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = " / ${session.totalCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textTertiary,
                )
            }
        }
        ProgressBar(solved = session.solvedCount, total = session.totalCount)
    }
}

/** '오늘의 한입' 배지 — info 톤 알약. ⚠️ 사용자에게 '세션'이라 부르지 않는다. */
@Composable
private fun TodayBiteBadge() {
    val colors = LocalBcsColors.current
    Text(
        text = "오늘의 한입",
        style = MaterialTheme.typography.labelMedium,
        color = colors.onInfoContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.infoContainer)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1),
    )
}

/**
 * 분량 진행 막대. 인접한 `N / M` 텍스트가 의미의 원천이므로 막대 자체는 장식이지만,
 * 스크린리더에는 진행을 한 줄로 실어 준다.
 */
@Composable
private fun ProgressBar(solved: Int, total: Int) {
    val colors = LocalBcsColors.current
    val primary = MaterialTheme.colorScheme.primary
    val fraction = if (total > 0) solved.toFloat() / total else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BcsDimens.space3)
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.border)
            .semantics { contentDescription = "오늘의 한입 진행 총 ${total}문제 중 ${solved}문제 완료" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(BcsDimens.space3)
                .clip(RoundedCornerShape(BcsDimens.radiusFull))
                .background(primary),
        )
    }
}

/** 오늘 몫 완료 — 긍정 빈 상태(§5.10). success 톤은 여기(완료)에 쓰는 게 맞다(§6.2). */
@Composable
private fun CompletedCard() {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.successContainer)
            .padding(BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text(
            text = "오늘 몫은 다 했어요!",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSuccessContainer,
        )
        Text(
            text = "원한다면 조금 더 풀어볼 수도 있어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSuccessContainer,
        )
    }
}

/**
 * 게스트 승계 유도 배너 — **권유**다. 막지 않고, 겁주지 않는다.
 * info 톤(§5.3 InfoCard)이라 danger·경고 신호가 끼어들 자리가 없다.
 */
@Composable
private fun GuestUpgradeBanner(onUpgrade: () -> Unit) {
    val colors = LocalBcsColors.current
    InfoCard {
        Text(
            text = "기록을 안전하게 지키세요",
            style = MaterialTheme.typography.labelLarge,
            color = colors.onInfoContainer,
        )
        Text(
            text = "가입하면 학습 기록과 연속 학습이 안전하게 저장돼요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        GhostButton(text = "가입하기", onClick = onUpgrade)
    }
}

/** §5.11 로딩 — 은은한 스켈레톤(스피너 지양). 실제 레이아웃(인사·진행·카드)의 자리를 잡아 준다. */
@Composable
private fun HomeSkeleton() {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Spacer(Modifier.height(BcsDimens.space10))
        Box(
            Modifier.fillMaxWidth(0.6f).height(BcsDimens.skeletonLine)
                .clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle),
        )
        Box(
            Modifier.fillMaxWidth().height(BcsDimens.space3)
                .clip(RoundedCornerShape(BcsDimens.radiusFull)).background(colors.border),
        )
        Spacer(Modifier.height(BcsDimens.space6))
        Box(
            Modifier.fillMaxWidth().height(BcsDimens.inputHeight)
                .clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle),
        )
    }
}

/**
 * §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도.
 * §5.10 EmptyState 골격(아이콘 원형 + 제목 + 설명 + Primary 액션, 세로·가로 중앙)을 따른다.
 *
 * ⭐️ danger를 쓰지 않는다 — 시스템 오류는 파괴적 행동이 아니고, 사용자 잘못은 더더욱 아니다(§2.2).
 */
@Composable
private fun HomeError(onRetry: () -> Unit) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BcsDimens.space5)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(BcsDimens.emptyStateIcon)
                .clip(CircleShape)
                .background(colors.surfaceSubtle),
            contentAlignment = Alignment.Center,
        ) {
            // 경고 삼각형·빨강 대신 중립 글리프. '연결이 잠깐 안 됐다'지 '무언가 잘못됐다'가 아니다.
            Text(text = "☁", style = MaterialTheme.typography.displaySmall, color = colors.textTertiary)
        }
        Text(
            text = "오늘의 한입을 불러오지 못했어요",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = "다시 시도하기", onClick = onRetry)
    }
}
