package watson.bytecs

import androidx.compose.runtime.Composable

/**
 * 물리 키보드 Enter를 [enabled] 동안 잡아 [onEnter]를 호출한다.
 *
 * ⭐️ 웹 전용 문제 해결책: 문제 정답 후 편집 입력칸이 확정 표시로 교체되면, IME 액션(Enter)을 받던
 * TextField가 사라진다. 웹(skiko 캔버스)에서는 Compose 포커스를 다른 요소로 옮겨도 캔버스가 브라우저
 * 키 입력을 계속 받지 못해(사용자가 화면을 한 번 클릭하기 전까지 Enter가 무반응) — 이를 우회하려
 * window 레벨 keydown 리스너로 Enter를 잡는다(wasmJs actual).
 *
 * 물리 키가 없는 모바일(Android·iOS)과 테스트 실행기(JVM)는 no-op다 — SystemBackHandler와 같은
 * 플랫폼 위임 방식이라 별도 단위 테스트 대상 로직이 없고, 웹 동작은 브라우저 스모크로 검증한다.
 */
@Composable
expect fun PhysicalEnterKey(enabled: Boolean, onEnter: () -> Unit)
