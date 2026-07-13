package watson.bytecs.problem.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

actual fun defaultHttpClientEngine(): HttpClientEngine = Darwin.create()

// 시뮬레이터는 호스트와 loopback을 공유하므로 localhost로 접근한다.
actual fun platformApiBaseUrl(): String = "http://localhost:8080"
