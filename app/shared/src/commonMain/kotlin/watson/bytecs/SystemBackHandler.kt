package watson.bytecs

import androidx.compose.runtime.Composable

/**
 * 시스템(하드웨어/제스처) 뒤로가기를 앱의 명시적 백스택에 연결하기 위한 플랫폼 훅.
 *
 * ⭐️ [실기기 QA] 안드로이드만 실제 뒤로가기 디스패처에 연결한다 — 연결이 없으면 하드웨어 뒤로가기가
 * 커스텀 백스택과 무관하게 액티비티를 곧장 종료해, 여러 화면을 쌓아둔 상태에서도 앱이 닫혔다. 나머지
 * 플랫폼(iOS·데스크톱 테스트)은 no-op다(각 플랫폼의 뒤로가기 관례는 이 슬라이스 범위 밖).
 *
 * commonMain에서 androidx `BackHandler`를 직접 쓰지 못하는 이유: 이 CMP 버전은 JVM(데스크톱) 타깃
 * compose.ui에 backhandler 패키지가 없어 commonMain 컴파일이 깨진다 — 그래서 expect/actual로 가른다.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
