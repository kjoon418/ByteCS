import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// 웹 런처 모듈: wasmJs 실행 바이너리만 갖는다. UI/로직 본체는 :app:shared(commonMain)에 있다.
// :app:shared를 순수 라이브러리로 유지하려고 실행 바이너리를 이 모듈로 분리했다(계획 §4).
kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "bytecs.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app.shared)
            // ComposeViewport(엔트리포인트)용. 문자열 접근자(compose.ui)는 deprecated라
            // :app:shared와 동일하게 버전 카탈로그로 참조한다.
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
        }
    }
}
