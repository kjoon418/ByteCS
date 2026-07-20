package watson.bytecs.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import watson.bytecs.ui.theme.BcsDimens
import watson.bytecs.ui.theme.LocalBcsColors

/**
 * 웹/데스크톱(EXPANDED) 리스트-디테일 2패널. 좌측 목록(master)을 고정 폭으로, 우측 상세(detail)를 남은
 * 폭으로 나란히 둔다. COMPACT/MEDIUM에서는 이 컴포넌트를 쓰지 않고 기존 목록→상세 push 흐름을 유지한다
 * (호출부가 너비 클래스로 분기). 목록·상세 컴포저블은 그대로 재사용하고, **선택 상태만 호출부(상위)로
 * 끌어올린다** — 상세 선택 시 백스택에 push하지 않고 우측 패널만 바뀐다(계획 §4-2).
 *
 * 스크랩·카테고리 이력 등 같은 목록→상세 구조의 화면이 공유한다.
 */
@Composable
fun TwoPaneListDetail(
    master: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    masterWidth: Dp = BcsDimens.masterPaneWidth,
) {
    val colors = LocalBcsColors.current
    Row(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.width(masterWidth).fillMaxHeight()) {
            master()
        }
        // 두 패널을 시각적으로 가르는 얇은 경계선.
        Box(
            modifier = Modifier
                .width(BcsDimens.borderWidth)
                .fillMaxHeight()
                .background(colors.borderSubtle),
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            detail()
        }
    }
}

/**
 * 2패널 우측 상세가 아직 선택되지 않았을 때의 빈 상태. 죄책감·오류가 아니라 조용한 안내다("선택하세요").
 * 배경을 채워 좌측 목록과 같은 표면 톤을 유지한다.
 */
@Composable
fun TwoPaneDetailPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBcsColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(BcsDimens.space6),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
