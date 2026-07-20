package watson.bytecs.problem.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import kotlinx.browser.window

actual fun defaultHttpClientEngine(): HttpClientEngine = Js.create()

/**
 * 같은 오리진 서빙(오너 결정 1)이 전제다.
 *
 * 레포지토리들이 `"$baseUrl/api/..."` 로 URL을 조립하고, App.kt가 `Url(platformApiBaseUrl()).host`로
 * 토큰 호스트 스코프를 잡는다. 둘 다 절대 URL을 필요로 하므로 `window.location.origin`이 정합하다.
 * 배포 시엔 웹 번들을 서빙하는 서버가 곧 API 서버이고, dev server에서는 프록시가 /api를 서버로
 * 전달하되 오리진은 dev server로 유지되어 스코프가 일관된다.
 */
actual fun platformApiBaseUrl(): String = window.location.origin
