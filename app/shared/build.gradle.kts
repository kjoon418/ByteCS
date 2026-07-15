import org.jetbrains.kotlin.gradle.dsl.JvmTarget
// java.util/java.io를 정규화된 이름으로 쓰면 Kotlin DSL의 `java` 확장이 패키지명을 가려 컴파일이 깨진다.
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

/**
 * Android 백엔드 베이스 URL을 소스 코드로 굽는다.
 *
 * 값이 개발 환경마다 달라야 하므로(에뮬레이터=10.0.2.2, adb reverse 실기기=localhost, LAN 직결=PC의 IP)
 * 커밋 가능한 소스에 상수로 둘 수 없고, 그렇다고 런타임 설정 화면을 두기엔 개발용 편의에 비해 과하다.
 * 빌드 시점 주입이 절충안이다.
 *
 * 이 모듈은 신규 AGP KMP 플러그인(androidLibrary)을 쓰는데 여기엔 buildConfigField가 없어
 * BuildConfig 대신 소스 생성으로 같은 효과를 낸다.
 */
abstract class GenerateApiConfigTask : DefaultTask() {
    @get:Input
    abstract val apiBaseUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        // 값이 URL이라 보통은 이스케이프가 불필요하지만, 문자열 리터럴에 그대로 박는 이상
        // 따옴표·역슬래시·$(템플릿 시작 문자)가 들어오면 빌드 스크립트가 깨진다. 방어적으로 막는다.
        val escaped = apiBaseUrl.get()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\${'$'}")
        val packageDir = outputDir.get().asFile.resolve("watson/bytecs/problem/data")
        packageDir.mkdirs()
        packageDir.resolve("ApiConfig.kt").writeText(
            """
            |// 이 파일은 빌드가 생성한다. 직접 수정하지 말 것. (app/shared/build.gradle.kts 참고)
            |package watson.bytecs.problem.data
            |
            |internal const val ANDROID_API_BASE_URL: String = "$escaped"
            |
            """.trimMargin()
        )
    }
}

/**
 * 우선순위: local.properties > -P 프로퍼티 > 기본값(에뮬레이터).
 * local.properties는 gitignore 대상이라 개인 IP가 커밋될 일이 없어 1순위로 둔다.
 *
 * 구성 캐시(configuration cache)를 쓰는 빌드라 파일 읽기는 반드시 Provider로 해야 한다.
 * File()/FileInputStream을 실행 시점에 직접 쓰면 캐시 무효화 추적이 끊긴다.
 */
val apiBaseUrlProvider: Provider<String> = providers.fileContents(
    isolated.rootProject.projectDirectory.file("local.properties")
).asText.map { text ->
    Properties()
        .apply { load(StringReader(text)) }
        .getProperty("bytecs.apiBaseUrl")
        .orEmpty()
}.filter { it.isNotBlank() }
    .orElse(providers.gradleProperty("bytecs.apiBaseUrl"))
    .orElse("http://10.0.2.2:8080")

val generateApiConfig = tasks.register<GenerateApiConfigTask>("generateApiConfig") {
    apiBaseUrl.set(apiBaseUrlProvider)
    outputDir.set(layout.buildDirectory.dir("generated/bytecs/androidMain/kotlin"))
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // 데스크톱(JVM) 타겟은 배포 대상이 아니라 **테스트 실행기**다.
    // Compose UI 테스트를 돌리려면 실제 컴포지션을 그릴 수 있는 타겟이 필요한데,
    // Android instrumented 테스트는 에뮬레이터가 있어야 해 로컬·CI 모두 무겁다.
    // JVM은 에뮬레이터 없이 `gradlew :app:shared:jvmTest`로 즉시 돌아가고,
    // 이미 있던 jvmMain/jvmTest 고아 디렉터리도 이 선언으로 비로소 빌드에 편입된다.
    jvm()

    androidLibrary {
        namespace = "watson.bytecs.app.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain {
            // 태스크 프로바이더를 넘겨야 컴파일이 생성 태스크에 의존한다는 사실이 자동 추론된다.
            // 경로 문자열만 넘기면 의존성이 끊겨 첫 빌드에서 상수를 못 찾는다.
            kotlin.srcDir(generateApiConfig)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmTest.dependencies {
            // Compose UI 테스트의 실행 런타임. desktop.currentOs가 호스트 OS용 skiko를 끌어와
            // 헤드리스 JVM에서도 컴포지션을 실제로 그릴 수 있게 한다.
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.uiTest)
        }
        commonMain.dependencies {
            api(projects.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.noArg)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
