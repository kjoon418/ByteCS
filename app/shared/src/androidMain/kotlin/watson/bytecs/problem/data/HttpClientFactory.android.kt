package watson.bytecs.problem.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()

// 에뮬레이터에서 호스트 머신의 localhost는 10.0.2.2로 접근한다.
actual fun platformApiBaseUrl(): String = "http://10.0.2.2:8080"
