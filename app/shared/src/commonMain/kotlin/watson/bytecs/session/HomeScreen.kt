package watson.bytecs.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 02 홈('오늘의 한입'). 재방문 사용자의 기본 진입점 — 오늘의 세션을 시작하거나 이어서 한다.
 *
 * ⭐️ 가벼운 초대·무낙인: "오늘도 한입 해볼까요?" 톤, 분량 기반 진행(타이머 없음), 완료는 긍정 빈 상태,
 * 스트릭은 긍정 동기(끊겨도 죄책감 연출 금지). 가입은 권유로만(막지 않기).
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

@Composable
private fun HomeScreenContent(
    state: HomeUiState,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onOpenAccount: () -> Unit,
    onUpgrade: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current

    BcsScaffold(
        modifier = modifier,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space2),
            ) {
                TextLink(
                    text = "계정",
                    onClick = onOpenAccount,
                    color = colors.textSecondary,
                    contentDescription = "계정·설정 열기",
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        },
        bottomBar = {
            // 주요 액션은 엄지 영역 하단 고정(00 §3.1). 상태에 따라 시작/이어서/추가연습으로 전환.
            (state as? HomeUiState.Ready)?.let { ready ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    when {
                        ready.isCompleted -> GhostButton(text = "조금 더 풀어보기", onClick = onExtraPractice)
                        ready.isInProgress -> PrimaryButton(text = "이어서 풀기", onClick = onStartOrContinue)
                        else -> PrimaryButton(text = "오늘의 한입 시작하기", onClick = onStartOrContinue)
                    }
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = BcsDimens.space5),
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space5),
        ) {
            when (state) {
                HomeUiState.Loading -> HomeSkeleton()
                HomeUiState.Error -> HomeError(onRetry = onRetry)
                is HomeUiState.Ready -> HomeReady(
                    state = state,
                    onUpgrade = onUpgrade,
                )
            }
        }
    }
}

@Composable
private fun HomeReady(
    state: HomeUiState.Ready,
    onUpgrade: () -> Unit,
) {
    val colors = LocalBcsColors.current
    val session = state.session

    Spacer(Modifier.height(BcsDimens.space6))

    // 인사 헤더 — 가벼운 초대 톤.
    Text(
        text = "오늘도 한입 해볼까요?",
        style = MaterialTheme.typography.titleLarge,
        color = colors.textPrimary,
    )

    // 스트릭 — 항상 긍정 톤. 0/끊김이어도 죄책감·상실 공포 대신 "다시 시작해요" 톤(UX 다크패턴 방지).
    session.streak?.let { streak ->
        StreakIndicator(count = streak.count)
    }

    // 진행 요약 — 분량 기반(시간 아님).
    SessionProgressSummary(solved = session.solvedCount, total = session.totalCount)

    Spacer(Modifier.height(BcsDimens.space2))

    // 오늘 완료 — 긍정 빈 상태(액션[조금 더 풀어보기]은 하단 CTA에 있다).
    if (state.isCompleted) {
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

    // 게스트 가입 유도(은은·권유). 막지 않는다.
    if (!state.isMember) {
        Spacer(Modifier.height(BcsDimens.space2))
        GuestUpgradeBanner(onUpgrade = onUpgrade)
    }

    Spacer(Modifier.height(BcsDimens.space6))
}

/** 분량 기반 진행 요약: "오늘 N / M" + 얇은 진행 막대. 카운트다운 타이머 아님. */
@Composable
private fun SessionProgressSummary(solved: Int, total: Int) {
    val colors = LocalBcsColors.current
    val primary = MaterialTheme.colorScheme.primary
    val fraction = if (total > 0) solved.toFloat() / total else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "오늘 세션 진행 총 ${total}문제 중 ${solved}문제 완료" },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text(
            text = "오늘 $solved / $total 문제",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BcsDimens.space2)
                .clip(RoundedCornerShape(BcsDimens.radiusFull))
                .background(colors.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(BcsDimens.space2)
                    .clip(RoundedCornerShape(BcsDimens.radiusFull))
                    .background(primary),
            )
        }
    }
}

/**
 * 스트릭 표시 — 항상 긍정 동기. count>0이면 성취로 또렷이, 0/끊김이면 "오늘 다시 시작해요" 초대 톤.
 * ⭐️ 끊김에 죄책감·상실 공포 연출 금지(UX 4 다크 패턴).
 */
@Composable
private fun StreakIndicator(count: Int) {
    val colors = LocalBcsColors.current
    val text = if (count > 0) "🔥 ${count}일 연속 학습 중" else "오늘 한입으로 연속 학습을 시작해요"
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = colors.onInfoContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.infoContainer)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1)
            .semantics { contentDescription = text },
    )
}

/** 게스트 승계 유도 배너 — 권유. */
@Composable
private fun GuestUpgradeBanner(onUpgrade: () -> Unit) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Text(
            text = "게스트로 이용 중이에요.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        GhostButton(text = "가입하고 기록 지키기", onClick = onUpgrade)
    }
}

/** §5.11 로딩 — 은은한 스켈레톤(스피너 지양). */
@Composable
private fun HomeSkeleton() {
    val colors = LocalBcsColors.current
    Spacer(Modifier.height(BcsDimens.space10))
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
        Box(
            Modifier.fillMaxWidth(0.6f).height(BcsDimens.skeletonLine)
                .clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle),
        )
        Box(
            Modifier.fillMaxWidth().height(BcsDimens.space2)
                .clip(RoundedCornerShape(BcsDimens.radiusFull)).background(colors.border),
        )
        Spacer(Modifier.height(BcsDimens.space6))
        Box(
            Modifier.fillMaxWidth().height(BcsDimens.inputHeight)
                .clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle),
        )
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun HomeError(onRetry: () -> Unit) {
    val colors = LocalBcsColors.current
    Spacer(Modifier.height(BcsDimens.space10))
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
