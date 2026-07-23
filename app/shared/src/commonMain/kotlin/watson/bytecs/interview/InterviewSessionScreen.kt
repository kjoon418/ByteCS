package watson.bytecs.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.ErrorBanner
import watson.bytecs.ui.components.ModelAnswerBlock
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.StreakBadge
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors
import watson.bytecs.ui.theme.LocalBcsType

/**
 * 08 면접 세션 화면. 승급된 개념에 대해 면접 방향(이름→설명)의 인출을 AI 루브릭 채점으로 검증한다 —
 * 일일 학습 세션(오늘의 한입)과 별개의 활동(회원 전용·하루 1세션·기본 3문제).
 *
 * ⭐️ 무낙인 채점: 점수·합불·퍼센트·빨강·× 금지. 미충족은 "보완하면 좋은 포인트"(중립 톤)로만.
 * ⭐️ 다른 촉감: 멀티라인 입력("내 말로 설명해보세요")으로 단답과 뚜렷이 구별한다.
 * ⭐️ 재제출 없음: 1문항 1채점. 나가기는 경고 없이(진행 중 세션은 서버에 영속 — 다시 들어오면 이어진다).
 *
 * @param onExit 부담 없는 나가기 → 홈(언제든 이어서).
 * @param onReport 오류 신고(대상은 그 면접 질문 콘텐츠 — promptId). null이면 링크를 그리지 않는다 —
 *   [review-todo] 서버에 면접 질문 콘텐츠 신고 엔드포인트가 아직 없어(신고는 Problem 대상만), 지금은
 *   호출부가 비워 둔다(문제 신고 화면을 promptId로 잘못 재사용하면 다른 콘텐츠가 신고되는 버그가 된다).
 * @param onReviewProblem '그때 푼 문제 다시 보기'(DI10) — 읽기 전용 재열람(스크랩·이력과 같은 패턴).
 * @param onFinish 세션 완료(또는 이미 완료 상태 진입) → 홈.
 */
@Composable
fun InterviewSessionScreen(
    viewModel: InterviewSessionViewModel,
    onExit: () -> Unit,
    onReport: ((Long) -> Unit)?,
    onReviewProblem: (Long) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 화면 진입마다 오늘 상태를 새로 반영한다(뷰모델이 내비게이션 간 재사용돼도 재개가 정확).
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    // 세션 완료는 일회성 이벤트 — 정확히 한 번 홈으로 넘긴다.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is InterviewEvent.Finished) onFinish()
        }
    }

    InterviewSessionScreenContent(
        state = state,
        onInputChange = viewModel::onInputChange,
        onSubmit = viewModel::submit,
        onAdvance = viewModel::advance,
        onFinish = viewModel::finish,
        onRetry = viewModel::load,
        onExit = onExit,
        onReport = onReport,
        onReviewProblem = onReviewProblem,
        modifier = modifier,
    )
}

@Composable
internal fun InterviewSessionScreenContent(
    state: InterviewUiState,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAdvance: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: ((Long) -> Unit)? = null,
    onReviewProblem: (Long) -> Unit = {},
) {
    val active = state as? InterviewUiState.Active

    BcsScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (active != null) {
                    watson.bytecs.ui.components.SessionProgress(current = active.current, total = active.total)
                }
                Spacer(Modifier.weight(1f))
                // 오류 신고(07) — 대상은 이 면접 질문 콘텐츠(질문·모범 설명·루브릭). 결과 단계에서도 열려 있다.
                // onReport가 null이면(서버에 대상 엔드포인트가 아직 없음) 링크 자체를 그리지 않는다.
                if (active != null && onReport != null) {
                    TextLink(
                        text = "오류 신고",
                        onClick = { onReport(active.item.promptId) },
                        color = LocalBcsColors.current.textTertiary,
                        contentDescription = "이 면접 질문의 콘텐츠 오류 신고",
                    )
                    Spacer(Modifier.width(BcsDimens.space4))
                }
                // 부담 없는 나가기(경고 모달 없음) — 진행 중 세션은 서버에 영속돼 다시 들어오면 이어진다.
                TextLink(
                    text = "나가기",
                    onClick = onExit,
                    color = LocalBcsColors.current.textSecondary,
                    contentDescription = "면접 연습에서 나가기, 언제든 이어서 할 수 있어요",
                )
            }
        },
        bottomBar = {
            // 채점 로딩 중에는 CTA를 두지 않는다(끼어들 조작 없음 — 디자인 08 §C). 그 외 단계에만 Primary를 그린다.
            if (active != null && !active.isGrading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    PrimaryButton(
                        text = when {
                            active.isLastItem -> "면접 연습 마치기"
                            active.isResult -> "다음 질문으로"
                            else -> "설명 제출하기"
                        },
                        onClick = when {
                            active.isLastItem -> onFinish
                            active.isResult -> onAdvance
                            else -> onSubmit
                        },
                        enabled = active.isResult || active.inputText.isNotBlank(),
                    )
                }
            }
        },
    ) {
        when (state) {
            InterviewUiState.Loading -> InterviewSkeleton(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            InterviewUiState.Error -> InterviewError(
                onRetry = onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = BcsDimens.space5),
            )

            is InterviewUiState.Active -> InterviewActiveContent(
                state = state,
                onInputChange = onInputChange,
                onSubmit = onSubmit,
                onReviewProblem = onReviewProblem,
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BcsDimens.space5),
            )
        }
    }
}

@Composable
private fun InterviewActiveContent(
    state: InterviewUiState.Active,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onReviewProblem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val focusRequester = remember { FocusRequester() }

    // 새 문항이 오면 입력에 자동 포커스(쓰기 단계에서만). 결과 단계에는 입력칸이 없다.
    LaunchedEffect(state.item.promptId, state.phase) {
        if (state.phase == InterviewPhase.Writing) focusRequester.requestFocus()
    }

    Column(modifier = modifier) {
        Spacer(Modifier.height(BcsDimens.space2))

        // 개념명(맥락) + 질문(화면의 주인공).
        Text(
            text = state.item.conceptName,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(BcsDimens.space2))
        Text(text = state.item.question, style = LocalBcsType.current.question, color = colors.textPrimary)

        Spacer(Modifier.height(BcsDimens.space6))

        when (state.phase) {
            InterviewPhase.Writing -> {
                ExplanationTextField(
                    value = state.inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.focusRequester(focusRequester),
                )
                if (state.systemError) {
                    Spacer(Modifier.height(BcsDimens.space4))
                    ErrorBanner(
                        message = "잠시 연결이 원활하지 않았어요. 다시 시도해 주세요.",
                        onRetry = onSubmit,
                    )
                }
            }

            InterviewPhase.Grading -> {
                // 제출한 설명을 잠긴 상태로 남겨 두고(편집 대상 아님) 아래에 명시적 채점 로딩을 둔다.
                LockedExplanation(text = state.inputText)
                Spacer(Modifier.height(BcsDimens.space5))
                GradingLoading()
            }

            InterviewPhase.Result -> {
                val result = state.result
                if (result != null) {
                    InterviewResultContent(
                        result = result,
                        completion = state.completion,
                        onReviewProblem = onReviewProblem,
                    )
                }
            }
        }

        Spacer(Modifier.height(BcsDimens.space6))
    }
}

/**
 * 결과 단계 본문 — 채점 성공이면 체크리스트, 폴백이면 안내 한 줄. 이어서 모범 설명, '검증됨' 미달이면
 * 재열람 링크(DI10), 마지막 문항이면 완료 요약 블록.
 */
@Composable
private fun InterviewResultContent(
    result: ItemResult,
    completion: InterviewCompletion?,
    onReviewProblem: (Long) -> Unit,
) {
    val judge = result.judge
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space5)) {
        if (judge.fallback) {
            // 폴백(채점 실패) — 에러처럼 다루지 않는다(ErrorBanner·danger 금지). 담백한 안내 + 모범 설명만.
            FallbackNotice()
        } else {
            RubricChecklistResult(judge)
        }

        // 모범 설명 — 성공·폴백 모두 공개(참고 자료 톤).
        ModelAnswerBlock(representativeAnswer = result.modelAnswer)

        // '검증됨' 미달(부분·미검증)일 때만 — 재열람 링크 + 복습 안내 병기(DI10). 폴백이면 그리지 않는다.
        if (!judge.fallback && !judge.verified) {
            ReviewNudge(reviewProblemId = result.reviewProblemId, onReviewProblem = onReviewProblem)
        } else if (!judge.fallback && judge.verified) {
            // 검증됨 — 짧은 긍정 한 줄로 충분(과장 없이). [draft — 카피는 UX 가이드로 확정 예정]
            Text(
                text = "이 개념은 면접에서도 잘 설명할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalBcsColors.current.textSecondary,
            )
        }

        // 완료 요약 블록(마지막 문항 결과에만) — 04의 큰 축하 연출을 가져오지 않는 담백한 확인.
        if (completion != null) {
            InterviewCompletionSummary(completion)
        }
    }
}

/**
 * §5.17 RubricChecklistResult — 포인트별 충족 여부 리스트. 상단 집계 한 줄 + 포인트 행 + (있으면) AI 코멘트.
 * ⛔ 점수·퍼센트·합격선·빨강·× 금지 — 미충족은 중립(`neutralNudge`) "보완하면 좋은 포인트" 톤만(무낙인).
 */
@Composable
private fun RubricChecklistResult(judge: ExplanationJudgeResult) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(MaterialTheme.colorScheme.surface)
            .border(BcsDimens.borderWidth, colors.borderSubtle, RoundedCornerShape(BcsDimens.radiusCard))
            .padding(BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        // 집계 한 줄 — 점수·퍼센트 없이 서술형만(무낙인). 라이브 리전으로 결과를 즉시 낭독한다.
        Text(
            text = "짚은 포인트 ${judge.satisfiedCount}개 / 보완하면 좋은 포인트 ${judge.unsatisfiedCount}개",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
            judge.points.forEach { point -> RubricPointRow(point) }
        }
        // AI 한 줄 코멘트(있을 때만).
        judge.comment?.let { comment ->
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * 루브릭 포인트 한 줄 — 충족=체크(success), 미충족=중립 아웃라인 + neutralNudge 톤 텍스트.
 * ⛔ 빨강·× 금지. 스크린리더는 아이콘이 아니라 "충족"/"보완하면 좋아요" 상태를 텍스트로 낭독한다(디자인 08 §15).
 */
@Composable
private fun RubricPointRow(point: RubricPoint) {
    val colors = LocalBcsColors.current
    val stateLabel = if (point.satisfied) "충족" else "보완하면 좋아요"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "${point.text}, $stateLabel" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        if (point.satisfied) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.success,
                modifier = Modifier.size(BcsDimens.iconMd).clearAndSetSemantics {},
            )
        } else {
            // 미충족 — 중립 아웃라인 원(빨강·× 대신). 아이콘 폰트 의존 없이 원으로 그린다.
            Box(
                modifier = Modifier
                    .size(BcsDimens.iconMd)
                    .clip(CircleShape)
                    .border(BcsDimens.borderWidthStrong, colors.neutralNudgeForeground, CircleShape)
                    .clearAndSetSemantics {},
            )
        }
        Text(
            text = point.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (point.satisfied) colors.textBody else colors.neutralNudgeForeground,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 폴백(채점 실패) 안내 — 담백한 톤(에러 아님). 모범 설명과 비교하도록 안내한다. */
@Composable
private fun FallbackNotice() {
    val colors = LocalBcsColors.current
    Text(
        text = "채점을 잠시 쉬어갈게요 — 모범 설명과 비교해보세요",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * '검증됨' 미달 결과의 재열람 링크 + 복습 안내(DI10). 재열람할 문제가 있으면 링크를, 없으면 안내만 그린다.
 * ⭐️ 강제·자동 이동 없음(무낙인) — "복습에서도 곧 다시 만나요"는 복습 당김(DI11) 덕에 사실인 문구다.
 */
@Composable
private fun ReviewNudge(
    reviewProblemId: Long?,
    onReviewProblem: (Long) -> Unit,
) {
    val colors = LocalBcsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
        if (reviewProblemId != null) {
            TextLink(
                text = "그때 푼 문제 다시 보기",
                onClick = { onReviewProblem(reviewProblemId) },
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "복습에서도 곧 다시 만나요",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * 완료 요약 블록(디자인 08 §11-b) — 04의 큰 축하 연출을 가져오지 않는 담백한 확인.
 * 스트릭 줄은 **이번 완료로 실제 기록된 경우에만**(completion.streak != null) 그린다 — 중복 축하 금지(DI5 하루 멱등).
 */
@Composable
private fun InterviewCompletionSummary(completion: InterviewCompletion) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .padding(BcsDimens.space4),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        Text(
            text = "오늘 개념 ${completion.practicedConceptCount}개를 면접처럼 설명해봤어요",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        completion.streak?.let { streak ->
            StreakBadge(days = streak.count)
        }
    }
}

/**
 * §5.17 GradingLoading — 채점 로딩(명시적 대기). 스켈레톤이 아니라 스피너+문구 조합이다(콘텐츠가 없는 게 아니라
 * 요청을 처리 중이라서). 취소 UI 없음(짧은 대기).
 */
@Composable
private fun GradingLoading() {
    val colors = LocalBcsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(BcsDimens.loaderSize),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = BcsDimens.loaderStroke,
        )
        Text(
            text = "설명을 채점하고 있어요…",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

/**
 * 제출 후 잠긴 설명 표시(채점 중). 더 이상 편집 대상이 아니므로 정적 표시로 바꾼다 — 재제출 경로가 구조적으로 사라진다.
 */
@Composable
private fun LockedExplanation(text: String) {
    val colors = LocalBcsColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .border(BcsDimens.borderWidth, colors.borderSubtle, RoundedCornerShape(BcsDimens.radiusCard))
            .padding(BcsDimens.space4),
    )
}

/**
 * 멀티라인 설명 입력 — 주관식 [watson.bytecs.ui.components.AnswerTextField]와 **의도적으로 다른 촉감**:
 * 여러 줄, 세로로 늘어나는 텍스트영역, placeholder "내 말로 설명해보세요". Enter는 줄바꿈이지 제출이 아니다
 * (제출은 하단 CTA로만 — 서술형은 여러 줄 작성이 자연스럽다). 최소 글자 수 등 과도한 차단은 두지 않는다(무낙인).
 */
@Composable
private fun ExplanationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (focused) MaterialTheme.colorScheme.primary else colors.border

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = false,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // 서술형이라 단답 입력의 두 배 이상 세로 공간을 확보한다(레이아웃 산식 — 토큰 배수).
                    .heightIn(min = BcsDimens.inputHeight * 2)
                    .clip(RoundedCornerShape(BcsDimens.radiusCard))
                    .background(colors.surfaceSubtle)
                    .border(BcsDimens.borderWidth, borderColor, RoundedCornerShape(BcsDimens.radiusCard))
                    .padding(horizontal = BcsDimens.space4, vertical = BcsDimens.space3),
                contentAlignment = Alignment.TopStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "내 말로 설명해보세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textTertiary,
                    )
                }
                innerTextField()
            }
        },
    )
}

/** 진입 스켈레톤(첫 질문 로딩) — 03과 동일 원칙(아직 콘텐츠가 없으므로 스켈레톤이 맞다). */
@Composable
private fun InterviewSkeleton(modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(modifier = modifier.padding(top = BcsDimens.space6), verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
        Box(Modifier.fillMaxWidth().height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Box(Modifier.fillMaxWidth(0.7f).height(BcsDimens.skeletonLine).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.borderSubtle))
        Spacer(Modifier.height(BcsDimens.space6))
        Box(Modifier.fillMaxWidth().height(BcsDimens.inputHeight * 2).clip(RoundedCornerShape(BcsDimens.radiusCard)).background(colors.surfaceSubtle))
    }
}

/** §5.12 로드 실패(시스템 오류) — 막다른 길 금지. 자산 안전 고지 + 재시도. */
@Composable
private fun InterviewError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalBcsColors.current
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space4, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("면접 연습을 불러오지 못했어요", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text("학습 기록은 안전해요. 잠시 후 다시 시도해 주세요.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        PrimaryButton(text = "다시 시도하기", onClick = onRetry)
    }
}
