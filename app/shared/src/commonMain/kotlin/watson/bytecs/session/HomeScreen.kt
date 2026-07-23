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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.interview.InterviewCardUiState
import watson.bytecs.interview.InterviewCardViewModel
import watson.bytecs.interview.InterviewPracticeCard
import watson.bytecs.ui.components.BcsCard
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.streakTone
import watson.bytecs.ui.layout.LocalWindowWidthClass
import watson.bytecs.ui.layout.WindowWidthClass
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
 * @param onExtraPractice 오늘 완료 후 '조금 더 풀어보기' → 새 세션으로 03 세션 풀이에 재진입(D6·D9 일원화 —
 * 추가 학습 폐지, 세션 밖 별도 화면 없음).
 * @param onOpenAccount 계정·설정(06).
 * @param onUpgrade 게스트 가입 유도 → 05.
 * @param onOpenScrapList 스크랩 목록 진입점(리뷰 반영, 2026-07-16 오너 결정) → 스크랩 목록.
 * @param onOpenCategoryHistory 카테고리별 학습 이력 진입점(기능 7, 1차) → 카테고리 목록.
 * @param onStartInterview 면접 연습 진입 카드(디자인 02 3-b, 잔여 있음 상태) → 08 면접 세션.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    interviewCardViewModel: InterviewCardViewModel,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onOpenAccount: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenScrapList: () -> Unit,
    onOpenCategoryHistory: () -> Unit,
    onStartInterview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val interviewCard by interviewCardViewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입(앱 재실행·세션 완료 후 복귀)마다 오늘 상태를 새로 반영한다. 면접 카드도 함께 갱신한다(잔여 쿼터 반영).
    LaunchedEffect(Unit) {
        viewModel.refresh()
        interviewCardViewModel.refresh()
    }

    HomeScreenContent(
        state = state,
        interviewCard = interviewCard,
        onStartOrContinue = onStartOrContinue,
        onExtraPractice = onExtraPractice,
        onOpenAccount = onOpenAccount,
        onUpgrade = onUpgrade,
        onOpenScrapList = onOpenScrapList,
        onOpenCategoryHistory = onOpenCategoryHistory,
        onStartInterview = onStartInterview,
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
    onOpenScrapList: () -> Unit = {},
    onOpenCategoryHistory: () -> Unit = {},
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    // 면접 연습 진입 카드(디자인 02 3-b). 기본은 [InterviewCardUiState.Hidden]이라 렌더되지 않는다 — 상태를
    // 주지 않는 기존 호출부(테스트 포함)는 카드가 없는 기존 화면 그대로다.
    interviewCard: InterviewCardUiState = InterviewCardUiState.Hidden,
    onStartInterview: () -> Unit = {},
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
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                HomeUiState.Loading -> HomeSkeleton()
                HomeUiState.Error -> HomeError(onRetry = onRetry)
                is HomeUiState.Ready -> HomeReady(
                    state = state,
                    interviewCard = interviewCard,
                    onStartOrContinue = onStartOrContinue,
                    onExtraPractice = onExtraPractice,
                    onUpgrade = onUpgrade,
                    onOpenScrapList = onOpenScrapList,
                    onOpenCategoryHistory = onOpenCategoryHistory,
                    onStartInterview = onStartInterview,
                )
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
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            tint = if (isMember) colors.onInfoContainer else colors.textTertiary,
            modifier = Modifier.size(BcsDimens.iconLg),
        )
    }
}

@Composable
private fun HomeReady(
    state: HomeUiState.Ready,
    interviewCard: InterviewCardUiState,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenScrapList: () -> Unit,
    onOpenCategoryHistory: () -> Unit,
    onStartInterview: () -> Unit,
) {
    // 웹/데스크톱(EXPANDED)에서는 세로 나열 대신 주 영역(오늘의 한입 카드)과 보조 컬럼(스트릭·진입점)으로
    // 2분할해 이동·스크롤을 줄인다(계획 §4-2). COMPACT/MEDIUM은 기존 단일 컬럼 그대로라 모바일 회귀가 없다.
    if (LocalWindowWidthClass.current == WindowWidthClass.EXPANDED) {
        HomeReadyExpanded(state, interviewCard, onStartOrContinue, onExtraPractice, onUpgrade, onOpenScrapList, onOpenCategoryHistory, onStartInterview)
    } else {
        HomeReadyColumn(state, interviewCard, onStartOrContinue, onExtraPractice, onUpgrade, onOpenScrapList, onOpenCategoryHistory, onStartInterview)
    }
}

/** COMPACT/MEDIUM(모바일·세로) — 세로 스크롤 단일 컬럼(기존 렌더 그대로). */
@Composable
private fun HomeReadyColumn(
    state: HomeUiState.Ready,
    interviewCard: InterviewCardUiState,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenScrapList: () -> Unit,
    onOpenCategoryHistory: () -> Unit,
    onStartInterview: () -> Unit,
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

        // 스트릭 — §5.16 승격(2026-07-16 오너 결정). 지속 동기의 핵심 장치라 독립 카드로 보여준다.
        // 끊겨도(0일) 죄책감·상실 공포 대신 "다시 시작해요" 톤이고, 색 규칙은 streakTone에 못박혀 있다.
        session.streak?.let { streak ->
            StreakCard(days = streak.count)
        }

        // 오늘 완료 상태는 별도 카드가 아니라 이 카드 자체의 시각 변화로 표현한다
        // (2026-07-16 오너 결정 — 홈 복잡도 감소). 긍정 빈 상태(§5.10)는 카드 안에서 이어진다.
        TodayBiteCard(
            state = state,
            onStartOrContinue = onStartOrContinue,
            onExtraPractice = onExtraPractice,
        )

        // 면접 연습 진입 카드(디자인 02 3-b) — 오늘의 한입 카드보다 아래 위계(계단 위계 DI1). 게스트 카드는 가입 유도로 이어진다.
        InterviewPracticeCard(state = interviewCard, onStart = onStartInterview, onUpgrade = onUpgrade)

        // 스크랩 목록 진입점(리뷰 반영) — 조용한 secondary 행. 히어로(오늘의 한입 CTA)를 방해하지 않는다.
        ScrapEntryRow(onOpenScrapList = onOpenScrapList)

        // 카테고리별 학습 이력 진입점(기능 7, 1차) — 스크랩 진입점과 같은 관례(조용한 secondary 행).
        CategoryHistoryEntryRow(onOpenCategoryHistory = onOpenCategoryHistory)

        // 게스트 가입 유도(은은·권유). 막지 않는다. 가입자에겐 아예 나오지 않는다(배타적).
        if (!state.isMember) {
            GuestUpgradeBanner(onUpgrade = onUpgrade)
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/**
 * EXPANDED(웹/데스크톱, ≥840dp) — 2컬럼. 히어로인 '오늘의 한입' 카드를 주 영역(좌, 더 넓게)에 두고,
 * 스트릭·진입점·가입 유도를 보조 컬럼(우)으로 뺀다. 컨테이너 폭을 [BcsDimens.contentMaxWide]로 제한하고
 * 중앙 정렬해 과폭(가독성 저하)을 막는다 — "넓다고 더 채우지 않는다"는 UX 원칙(과밀 금지).
 */
@Composable
private fun HomeReadyExpanded(
    state: HomeUiState.Ready,
    interviewCard: InterviewCardUiState,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenScrapList: () -> Unit,
    onOpenCategoryHistory: () -> Unit,
    onStartInterview: () -> Unit,
) {
    val session = state.session

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = BcsDimens.contentMaxWide)
                .fillMaxWidth()
                .padding(horizontal = BcsDimens.space6, vertical = BcsDimens.space6),
            horizontalArrangement = Arrangement.spacedBy(BcsDimens.space6),
        ) {
            // 주 영역(좌): 인사 + 오늘의 한입 히어로 카드. 시선이 먼저 닿는 곳에 시작 CTA를 둔다.
            Column(
                modifier = Modifier.weight(1.4f),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space5),
            ) {
                Greeting()
                TodayBiteCard(
                    state = state,
                    onStartOrContinue = onStartOrContinue,
                    onExtraPractice = onExtraPractice,
                )
            }
            // 보조 컬럼(우): 스트릭 + 진입점 + 가입 유도. 히어로를 방해하지 않는 부차 정보만 모은다.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space5),
            ) {
                session.streak?.let { streak ->
                    StreakCard(days = streak.count)
                }
                // 면접 연습 진입 카드(디자인 02 3-b) — 보조 컬럼에 둬 히어로(오늘의 한입)를 방해하지 않는다.
                InterviewPracticeCard(state = interviewCard, onStart = onStartInterview, onUpgrade = onUpgrade)
                ScrapEntryRow(onOpenScrapList = onOpenScrapList)
                CategoryHistoryEntryRow(onOpenCategoryHistory = onOpenCategoryHistory)
                if (!state.isMember) {
                    GuestUpgradeBanner(onUpgrade = onUpgrade)
                }
            }
        }
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
 * 오늘의 한입 카드 — 배지 + 상태 제목 + 분량 진행(`2 / 10`) + CTA 버튼.
 * ⭐️ 분량 기반이다. 카운트다운 타이머가 아니다(§5.4).
 *
 * 완료 시 별도 카드를 얹지 않고 **이 카드 자체가 시각적으로 변한다**(2026-07-16 오너 결정 — 홈 복잡도
 * 감소). 배지가 완료 표식으로, 진행 막대가 success 색으로 바뀌고, 긍정 빈 상태 문구(§5.10)가 안에 붙는다.
 *
 * CTA 배치는 최신안(`docs/design/02 홈 오늘의 한입 디자인 최신안.html`)을 따른다 — 진행 막대 바로 아래,
 * 완료 시 안내 문구 위에 전폭 버튼을 둔다(QA #8, 2026-07-17). 하단 고정 바가 아니라 카드 안에 있어야
 * 버튼을 누르면 무엇이 시작되는지 맥락이 바로 붙는다. 최신안의 배지·프로필·분량 표기 등 나머지 요소는
 * 오너 결정으로 폐기됐으므로 반영하지 않는다.
 */
@Composable
private fun TodayBiteCard(
    state: HomeUiState.Ready,
    onStartOrContinue: () -> Unit,
    onExtraPractice: () -> Unit,
) {
    val colors = LocalBcsColors.current
    val session = state.session
    val completed = state.isCompleted
    // success는 정답·완료에 쓰는 게 맞다(§6.2) — onSuccessContainer는 옅은 표면 위 판독성을 보장하도록
    // 골라진 색이라, 카드 배경이 그보다 더 옅은 surface여도 대비가 그대로 유지된다.
    val titleColor = if (completed) colors.onSuccessContainer else colors.textPrimary
    val title = when {
        completed -> "오늘 몫은 다 했어요!"
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
                if (completed) CompletedBadge() else TodayBiteBadge()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor,
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
        ProgressBar(
            solved = session.solvedCount,
            total = session.totalCount,
            barColor = if (completed) colors.success else MaterialTheme.colorScheme.primary,
        )
        when {
            completed -> GhostButton(text = "조금 더 풀어보기", onClick = onExtraPractice)
            state.isInProgress -> PrimaryButton(text = "학습 이어서 하기", onClick = onStartOrContinue)
            else -> PrimaryButton(text = "오늘의 한입 시작하기", onClick = onStartOrContinue)
        }
        if (completed) {
            Text(
                text = "원한다면 조금 더 풀어볼 수도 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
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

/** 완료 배지 — [TodayBiteBadge]의 success 변형. 체크 표식으로 '오늘의 한입'을 대신한다. */
@Composable
private fun CompletedBadge() {
    val colors = LocalBcsColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(BcsDimens.radiusFull))
            .background(colors.success)
            .padding(horizontal = BcsDimens.space3, vertical = BcsDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space1),
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = colors.onSuccess,
            modifier = Modifier.size(BcsDimens.iconSm),
        )
        Text(
            text = "완료",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSuccess,
        )
    }
}

/**
 * 분량 진행 막대. 인접한 `N / M` 텍스트가 의미의 원천이므로 막대 자체는 장식이지만,
 * 스크린리더에는 진행을 한 줄로 실어 준다. [barColor]는 완료 시 success로 바뀐다.
 */
@Composable
private fun ProgressBar(
    solved: Int,
    total: Int,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val colors = LocalBcsColors.current
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
                .background(barColor),
        )
    }
}

/**
 * 스트릭 카드 — §5.16 승격(2026-07-16 오너 결정). 지속 동기의 핵심 장치라 알약 배지 대신
 * 독립 카드로 보여준다. 카드 표면은 중립([BcsCard])이고, 아이콘 타일 색만 [streakTone]을 따른다
 * — 끊겨도 카드 전체가 물들지 않는다.
 *
 * ⚠️ 시안(02 html)의 "내일도 오시면 N일 연속이에요" 미래 문구는 뺐다 — 지키지 못하면(내일 못 오면)
 * 실망을 예약하는 손실 프레임이라는 우려로, 오늘 성취를 말하는 한 줄만 남긴다(오너 결정).
 * 끊김(0일)에는 불꽃 대신 중립 새싹 아이콘 — 상실 공포 연출 금지는 기존 StreakBadge 규칙 그대로다.
 */
@Composable
private fun StreakCard(days: Int) {
    val colors = LocalBcsColors.current
    val tone = streakTone(days, colors)
    val icon = if (days > 0) Icons.Rounded.LocalFireDepartment else Icons.Rounded.Eco
    val label = if (days > 0) "${days}일째 꾸준히 한입!" else "오늘 한입으로 연속 학습을 시작해요"

    BcsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BcsDimens.space4),
        ) {
            Box(
                modifier = Modifier
                    .size(BcsDimens.minTouchTarget)
                    .clip(RoundedCornerShape(BcsDimens.radiusCard))
                    .background(tone.background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tone.accent,
                    // 불꽃(살아 있음)과 새싹(끊김)을 테스트에서 구분하기 위한 태그.
                    modifier = Modifier
                        .size(BcsDimens.iconLg)
                        .testTag(if (days > 0) "streak-fire" else "streak-sprout"),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
    }
}

/**
 * 스크랩 목록 진입점(리뷰 반영, 2026-07-16 오너 결정) — 조용한 secondary 행.
 * 히어로인 [TodayBiteCard]/하단 CTA의 위계를 해치지 않도록 강조 없이, 04 완료 화면의 게스트 승계
 * 행([SessionCompleteScreen])과 같은 관례(라벨 + `›`)를 따른다.
 */
@Composable
private fun ScrapEntryRow(onOpenScrapList: () -> Unit) {
    val colors = LocalBcsColors.current
    BcsCard(onClick = onOpenScrapList) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        ) {
            Icon(
                imageVector = Icons.Rounded.Bookmarks,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(BcsDimens.iconMd),
            )
            Text(
                text = "스크랩한 문제",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = "›", style = MaterialTheme.typography.titleMedium, color = colors.textTertiary)
        }
    }
}

/**
 * 카테고리별 학습 이력 진입점(기능 7, 1차) — 조용한 secondary 행. [ScrapEntryRow]와 같은 관례
 * (라벨 + `›`)를 따른다 — 히어로(오늘의 한입 CTA)를 방해하지 않도록 강조 없이 둔다.
 */
@Composable
private fun CategoryHistoryEntryRow(onOpenCategoryHistory: () -> Unit) {
    val colors = LocalBcsColors.current
    BcsCard(onClick = onOpenCategoryHistory) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(BcsDimens.iconMd),
            )
            Text(
                text = "카테고리별 학습 이력",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = "›", style = MaterialTheme.typography.titleMedium, color = colors.textTertiary)
        }
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
            // 경고 삼각형·빨강 대신 중립 아이콘. '연결이 잠깐 안 됐다'지 '무언가 잘못됐다'가 아니다.
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(BcsDimens.iconXl),
            )
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
