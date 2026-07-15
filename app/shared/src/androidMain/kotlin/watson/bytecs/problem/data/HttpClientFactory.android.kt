package watson.bytecs.problem.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()

// 값은 빌드가 생성한다(app/shared/build.gradle.kts의 generateApiConfig).
// 기본값은 에뮬레이터용 10.0.2.2다. 이 주소는 에뮬레이터가 호스트 루프백으로 매핑하는 특수 주소라
// 실기기에서는 아무 의미가 없다(폰 자신을 가리켜 반드시 연결 실패).
// 실기기는 `adb reverse tcp:8080 tcp:8080`(USB 터널)로 붙고, local.properties에
// bytecs.apiBaseUrl=http://localhost:8080 을 지정한다. 절차는 docs/dev/Android 실기기 테스트 가이드.md.
actual fun platformApiBaseUrl(): String = ANDROID_API_BASE_URL
