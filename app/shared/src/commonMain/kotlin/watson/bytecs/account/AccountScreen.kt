package watson.bytecs.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.GhostButton
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.PrimaryButtonRole
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors
import watson.bytecs.ui.theme.ThemeMode

/**
 * 06 계정·설정 화면. 데이터 통제권을 명확·안전하게 제공한다.
 *
 * ⭐️ 무낙인·신뢰:
 *  - 게스트는 로그아웃/프로필 대신 "가입하고 기록 지키기" CTA를 본다.
 *  - 계정 삭제는 이 화면에서 danger(빨강)를 쓰는 **유일한** 지점 — 신중한 확인 단계를 강제하되 공포 연출은 피한다.
 *  - 로그아웃에는 "다시 로그인하면 기록이 그대로예요" 안심 문구를 곁들인다.
 *
 * @param onNavigateToLogin 게스트의 가입 CTA → 05 로그인·가입.
 * @param onDeleted 계정 삭제 완료 → 온보딩(범위 밖이라 지금은 문제 화면으로 라우팅).
 * @param onBack 상단 뒤로.
 */
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onNavigateToLogin: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onOpenScrapList: () -> Unit,
    appVersion: String = "0.1.0",
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 진입 시 화면 전용 상태 초기화(재사용 뷰모델의 이전 삭제 단계·편집값 잔류 제거).
    LaunchedEffect(Unit) {
        viewModel.resetForEntry()
    }

    AccountScreenContent(
        state = state,
        appVersion = appVersion,
        onBack = onBack,
        onNavigateToLogin = onNavigateToLogin,
        onOpenScrapList = onOpenScrapList,
        onSessionSizeChange = viewModel::onSessionSizeChange,
        onSaveSettings = viewModel::saveSettings,
        onThemeSelect = viewModel::setThemeMode,
        onLogout = viewModel::logout,
        onRequestDelete = viewModel::requestDelete,
        onCancelDelete = viewModel::cancelDelete,
        onConfirmDelete = { viewModel.confirmDelete(onDeleted) },
        modifier = modifier,
    )
}

@Composable
internal fun AccountScreenContent(
    state: AccountUiState,
    appVersion: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onOpenScrapList: () -> Unit,
    onSessionSizeChange: (Int) -> Unit,
    onSaveSettings: () -> Unit,
    onThemeSelect: (ThemeMode) -> Unit,
    onLogout: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current

    // 확인 다이얼로그(§5.13)를 화면 위에 띄우기 위한 오버레이 레이어.
    Box(modifier = modifier.fillMaxSize()) {
        BcsScaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space4),
                ) {
                    TextLink(
                        text = "뒤로",
                        onClick = onBack,
                        color = colors.textSecondary,
                        contentDescription = "뒤로 가기",
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    Text(
                        text = "계정·설정",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = BcsDimens.space5),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space5),
            ) {
                AccountStatusHeader(state = state, onNavigateToLogin = onNavigateToLogin)

                // 토큰은 유효하나 프로필만 못 불러온 회원: 인증 실패가 아니라 가벼운 안내.
                if (state.profileError) {
                    InfoNotice("프로필을 불러오지 못했어요. 잠시 후 자동으로 다시 채워져요.")
                }

                // 시안 외 최소 진입점(기획 리뷰 대상) — 스크랩 목록으로 가는 관례적 내비게이션 행.
                // 게스트·회원 모두 스크랩을 쓸 수 있으므로 상태와 무관하게 노출한다.
                SectionTitle("내 학습")
                GhostButton(
                    text = "스크랩한 문제",
                    onClick = onOpenScrapList,
                    contentColor = colors.textPrimary,
                )

                SectionTitle("학습 설정")
                SessionSizeSetting(
                    size = state.sessionSize,
                    error = state.sessionSizeError,
                    dirty = state.isSettingsDirty,
                    saving = state.isSettingsSaving,
                    onChange = onSessionSizeChange,
                    onSave = onSaveSettings,
                )

                SectionTitle("화면 테마")
                ThemeToggle(selected = state.themeMode, onSelect = onThemeSelect)

                // 설정 저장·로그아웃 등 일반 오류. 계정 삭제 오류는 확인 다이얼로그 안에서만 보여준다(채널 분리).
                if (state.noticeError != null) {
                    InfoNotice(state.noticeError)
                }

                // 로그아웃 — 회원에게만. 게스트에겐 로그아웃할 계정 자체가 없다.
                if (state.isMember) {
                    Spacer(Modifier.height(BcsDimens.space2))
                    LogoutRow(loggingOut = state.isLoggingOut, onLogout = onLogout)
                }

                // 계정 삭제 — 회원에게만, danger 허용 유일 지점.
                if (state.isMember) {
                    DeleteEntryButton(onRequestDelete = onRequestDelete)
                }

                Spacer(Modifier.height(BcsDimens.space4))
                // 앱 정보/버전 — 하단.
                Text(
                    text = "CS한입 v$appVersion",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(BcsDimens.space4))
            }
        }

        // §5.13 ConfirmDialog — 파괴적 행동 전용. 회원의 삭제 확인 단계에서만 등장한다.
        if (state.isMember && state.deletePhase != DeletePhase.None) {
            DeleteConfirmDialog(
                phase = state.deletePhase,
                error = state.deleteError,
                onCancel = onCancelDelete,
                onConfirm = onConfirmDelete,
            )
        }
    }
}

/** 계정 상태 헤더. 회원=이메일, 게스트="게스트로 이용 중" + 가입 CTA. */
@Composable
private fun AccountStatusHeader(state: AccountUiState, onNavigateToLogin: () -> Unit) {
    val colors = LocalBcsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.surfaceSubtle)
            .padding(BcsDimens.space5),
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        if (state.isMember) {
            Text(
                text = "로그인 중",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = state.email ?: "회원",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        } else {
            // ⭐️ "게스트 계정"이 아니라 "게스트로 이용 중" — 게스트에겐 계정이 없다는 게 가입 승계 모델의 전제다.
            Text(
                text = "게스트로 이용 중",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = "학습 기록이 기기에만 저장돼요. 가입하면 기기를 바꿔도 그대로 이어져요.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            PrimaryButton(text = "가입하고 기록 지키기", onClick = onNavigateToLogin)
        }
    }
}

/** 세션 크기 스텝퍼. 1~50 범위. 변경 시에만 저장 버튼 활성. */
@Composable
private fun SessionSizeSetting(
    size: Int,
    error: String?,
    dirty: Boolean,
    saving: Boolean,
    onChange: (Int) -> Unit,
    onSave: () -> Unit,
) {
    val colors = LocalBcsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "세션 크기",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3)) {
                // ⭐️ 범위 밖 값이 표시·낭독되지 않도록 콜백에서 클램프하고, 경계에서는 버튼을 비활성화한다.
                StepperButton(
                    symbol = "−",
                    description = "세션 크기 줄이기",
                    enabled = size > AccountViewModel.MIN_SESSION_SIZE,
                    onClick = { onChange((size - 1).coerceAtLeast(AccountViewModel.MIN_SESSION_SIZE)) },
                )
                Text(
                    text = "${size}문제",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { contentDescription = "세션 크기 ${size}문제" },
                )
                StepperButton(
                    symbol = "+",
                    description = "세션 크기 늘리기",
                    enabled = size < AccountViewModel.MAX_SESSION_SIZE,
                    onClick = { onChange((size + 1).coerceAtMost(AccountViewModel.MAX_SESSION_SIZE)) },
                )
            }
        }
        Text(
            text = "하루 세션에서 풀 문제 수예요",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (dirty) {
            PrimaryButton(text = "저장하기", onClick = onSave, loading = saving)
        }
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalBcsColors.current
    val contentColor = if (enabled) colors.textPrimary else colors.textTertiary
    Box(
        modifier = Modifier
            .size(BcsDimens.minTouchTarget)
            .clip(RoundedCornerShape(BcsDimens.radiusSm))
            .border(BcsDimens.borderWidth, colors.border, RoundedCornerShape(BcsDimens.radiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium, color = contentColor)
    }
}

/** 라이트/다크/시스템 선택. 선택된 항목만 primary로 강조. */
@Composable
private fun ThemeToggle(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        // 라디오 그룹으로 묶어 스크린리더가 "N/3 선택됨" 맥락을 읽게 한다.
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BcsDimens.space3),
    ) {
        ThemeOption("라이트", ThemeMode.LIGHT, selected, onSelect, Modifier.weight(1f))
        ThemeOption("다크", ThemeMode.DARK, selected, onSelect, Modifier.weight(1f))
        ThemeOption("시스템", ThemeMode.SYSTEM, selected, onSelect, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeOption(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    val isSelected = selected == mode
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .height(BcsDimens.minTouchTarget)
            .clip(RoundedCornerShape(BcsDimens.radiusChip))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else colors.surfaceSubtle)
            .border(
                BcsDimens.borderWidth,
                if (isSelected) primary else colors.border,
                RoundedCornerShape(BcsDimens.radiusChip),
            )
            .clickable(onClick = { onSelect(mode) })
            .semantics {
                this.role = Role.RadioButton
                this.selected = isSelected
                this.stateDescription = if (isSelected) "선택됨" else "선택 안 됨"
                this.contentDescription = "$label 테마"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else colors.textSecondary,
        )
    }
}

/** 로그아웃 행 — 안심 문구 포함. GhostButton(저강조 보조 액션)로 통일. */
@Composable
private fun LogoutRow(loggingOut: Boolean, onLogout: () -> Unit) {
    val colors = LocalBcsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(BcsDimens.space2)) {
        GhostButton(
            text = if (loggingOut) "로그아웃 중…" else "로그아웃",
            onClick = onLogout,
            enabled = !loggingOut,
            contentColor = colors.textPrimary,
        )
        Text(
            text = "다시 로그인하면 기록이 그대로예요.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * 계정 삭제 진입 — 목록에서 시각적으로 구분하되 위협적이지 않게(§3-5).
 * 글자만 danger로 두고 테두리는 중립으로 둬서, 평상시 화면에 빨강 면적이 생기지 않게 한다.
 * 실제 삭제는 [DeleteConfirmDialog]의 명시적 확인을 거쳐야만 일어난다.
 */
@Composable
private fun DeleteEntryButton(onRequestDelete: () -> Unit) {
    GhostButton(
        text = "계정 삭제",
        onClick = onRequestDelete,
        contentColor = MaterialTheme.colorScheme.error,
        borderColor = LocalBcsColors.current.border,
    )
}

/**
 * §5.13 ConfirmDialog — 파괴적 행동 전용. 이 화면에서만 쓰이므로 private으로 인라인한다.
 * bg `surface`, radius 16, padding 24dp, 제목 headingM, 설명 bodyS.
 *
 * ⭐️ 공포 연출 금지: 경고 배지·빨강 배경 없이 카드는 중립(surface)으로 두고,
 * danger는 [삭제] 버튼 하나에만 남긴다. 문구는 비난 없이 무엇이 사라지는지 사실만 말한다.
 */
@Composable
private fun DeleteConfirmDialog(
    phase: DeletePhase,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalBcsColors.current
    val colorScheme = MaterialTheme.colorScheme
    val deleting = phase == DeletePhase.Deleting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
            // ⭐️ 스크림 탭으로는 닫지 않는다. 되돌릴 수 없는 결정이라 취소도 명시적이어야 하고,
            // 뒤 목록으로 클릭이 새는 것도 막는다.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = BcsDimens.contentMax)
                .clip(RoundedCornerShape(topStart = BcsDimens.radiusSheet, topEnd = BcsDimens.radiusSheet))
                .background(colorScheme.surface)
                .padding(BcsDimens.space6)
                // 열리는 순간 스크린리더가 무엇이 사라지는지 읽어 주도록 라이브 리전으로 둔다.
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(BcsDimens.space3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "계정을 삭제할까요?",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "모든 학습 기록·숙련도·복습 일정이 삭제돼요. 되돌릴 수 없어요.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(BcsDimens.space2))
            // ⭐️ 취소가 먼저·기본 선택. 삭제는 아래에 두어 실수로 먼저 닿지 않게 한다.
            GhostButton(
                text = "취소",
                onClick = onCancel,
                enabled = !deleting,
                contentColor = colors.textPrimary,
            )
            // §5.13이 지정한 [삭제] = PrimaryButton(danger). Destructive 역할은 이 서비스에서 여기서만 쓴다.
            PrimaryButton(
                text = "삭제할게요",
                onClick = onConfirm,
                enabled = !deleting,
                loading = deleting,
                role = PrimaryButtonRole.Destructive,
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.4f

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = LocalBcsColors.current.textSecondary,
    )
}

/** 시스템 오류 안내 — info 톤(비처벌). */
@Composable
private fun InfoNotice(message: String) {
    val colors = LocalBcsColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BcsDimens.radiusCard))
            .background(colors.infoContainer)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(BcsDimens.space4),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onInfoContainer,
        )
    }
}
