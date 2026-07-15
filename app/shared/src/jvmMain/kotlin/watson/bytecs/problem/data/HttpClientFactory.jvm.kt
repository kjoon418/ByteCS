package watson.bytecs.problem.data

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

/**
 * JVM 타겟은 배포 대상이 아니라 UI 테스트 실행기다(app/shared/build.gradle.kts 참고).
 * 그래도 expect 선언은 모든 타겟에서 actual이 있어야 컴파일되므로, 가장 단순한 구현을 둔다.
 * 엔진은 Android와 같은 OkHttp를 쓰고, 베이스 URL은 호스트 루프백을 그대로 가리킨다.
 */
actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()

actual fun platformApiBaseUrl(): String = "http://localhost:8080"
