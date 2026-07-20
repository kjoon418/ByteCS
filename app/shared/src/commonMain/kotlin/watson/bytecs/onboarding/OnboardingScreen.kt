package watson.bytecs.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import watson.bytecs.ui.components.BcsScaffold
import watson.bytecs.ui.components.InfoCard
import watson.bytecs.ui.components.PrimaryButton
import watson.bytecs.ui.components.TextLink
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors
import watson.bytecs.ui.theme.LocalBcsType

/**
 * 01 온보딩 시작 화면(`docs/design/01 온보딩 시작 화면 디자인.html`). 최초 실행 1회만 노출된다
 * (노출 판단·영속은 [OnboardingStore], 게이팅은 [watson.bytecs.rootPhase]가 담당).
 *
 * ⭐️ 무낙인·저마찰: 가입을 강요하지 않는다 — "바로 시작하기"가 유일한 Primary이고, 로그인은 보조 링크다.
 * 상태가 없는 순수 표시 화면이라 뷰모델 없이 콜백만 받는다.
 */
@Composable
fun OnboardingScreen(
    onStart: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    BcsScaffold(
        modifier = modifier,
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BcsDimens.space5, vertical = BcsDimens.space6),
                verticalArrangement = Arrangement.spacedBy(BcsDimens.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 화면의 유일한 Primary. 게스트로 즉시 시작(가입 강요 없음).
                PrimaryButton(text = "바로 시작하기", onClick = onStart)
                TextLink(
                    text = "이미 계정이 있나요? 로그인",
                    onClick = onLogin,
                    contentDescription = "로그인하기",
                )
                // 승계 모델 안심 문구 — 시작 전에 "잃을 게 없다"를 먼저 고지한다.
                InfoCard {
                    Text(
                        text = "가입하지 않아도 시작할 수 있어요.\n나중에 가입하면 학습 기록이 그대로 이어져요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onInfoContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BcsDimens.space5),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(BcsDimens.space10))

            // 브랜드.
            Text(
                text = "CS한입",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(BcsDimens.space1))
            Text(
                text = "ByteCS",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textTertiary,
            )

            Spacer(Modifier.height(BcsDimens.space10))

            // 가치 제안 — 화면의 주인공.
            Text(
                text = "5분이면 CS 한입,\n오늘도 가볍게 채워보세요.",
                style = LocalBcsType.current.question,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(BcsDimens.space8))

            // 핵심 3가지 — 부담 없이 무엇을 하는지 한눈에.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top,
            ) {
                FeaturePoint("직접 풀며 익히기")
                FeaturePoint("오늘의 한입")
                FeaturePoint("막히면 힌트")
            }

            Spacer(Modifier.height(BcsDimens.space6))
        }
    }
}

/** 온보딩 특징 한 점 — 은은한 원형 장식 + 라벨. 아이콘 폰트 의존을 피해 단색 원으로 둔다. */
@Composable
private fun FeaturePoint(label: String) {
    val colors = LocalBcsColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BcsDimens.space2),
    ) {
        Box(
            modifier = Modifier
                .size(BcsDimens.space10)
                .clip(RoundedCornerShape(BcsDimens.radiusFull))
                .background(colors.infoContainer),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
