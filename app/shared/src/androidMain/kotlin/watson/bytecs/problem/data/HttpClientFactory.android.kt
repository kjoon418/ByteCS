package watson.bytecs.problem.data

import android.os.Build
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()

/** 에뮬레이터가 호스트 PC의 루프백으로 매핑해 두는 특수 주소. 실기기에서는 폰 자신을 가리켜 무의미하다. */
private const val EMULATOR_BASE_URL = "http://10.0.2.2:8080"

/** 실기기용. `adb reverse tcp:8080 tcp:8080`이 USB 터널로 PC의 8080에 넘겨준다. */
private const val DEVICE_BASE_URL = "http://localhost:8080"

/**
 * 앱이 바라볼 베이스 URL을 정한다.
 *
 * [configured]가 비어 있지 않으면(= 빌드 시점에 명시적으로 주입됨) 무조건 그 값이 이긴다.
 * LAN IP 직결·스테이징·프로덕션 주소를 넣는 탈출구다.
 *
 * 비어 있으면 실행 중인 기기를 보고 자동으로 고른다. 덕분에 에뮬레이터와 실기기를
 * local.properties 수정이나 재빌드 없이 번갈아 쓸 수 있다.
 *
 * [Build] 값을 직접 읽지 않고 인자로 받는다. 순수 함수라야 에뮬레이터나 Robolectric 없이 테스트된다.
 */
internal fun resolveApiBaseUrl(
    configured: String,
    fingerprint: String,
    product: String,
    hardware: String,
    model: String
): String {
    if (configured.isNotBlank()) return configured
    return if (looksLikeEmulator(fingerprint, product, hardware, model)) EMULATOR_BASE_URL else DEVICE_BASE_URL
}

/**
 * 에뮬레이터 판정. 안드로이드에 "나는 에뮬레이터다"라는 공식 API가 없어 빌드 정보로 추정한다.
 *
 * 신호 하나하나는 취약하다(제조사가 임의 값을 넣고, 에뮬레이터 이미지마다 값이 다르다).
 * OR로 묶어 하나만 걸려도 잡히게 한다. 오판해도 개발 빌드의 주소가 틀릴 뿐이라 이 정도 정밀도로 충분하다.
 */
private fun looksLikeEmulator(
    fingerprint: String,
    product: String,
    hardware: String,
    model: String
): Boolean {
    val lowerFingerprint = fingerprint.lowercase()
    val lowerProduct = product.lowercase()
    val lowerHardware = hardware.lowercase()
    val lowerModel = model.lowercase()

    // AOSP 표준 이미지는 빌드 주체가 generic/unknown으로 남는다. 실기기는 제조사명이 박힌다.
    return lowerFingerprint.startsWith("generic") ||
        lowerFingerprint.startsWith("unknown") ||
        lowerFingerprint.contains("emulator") ||
        // Android Studio 기본 이미지: sdk_gphone64_x86_64
        lowerProduct.contains("sdk") ||
        lowerProduct.contains("emulator") ||
        // goldfish=구 QEMU, ranchu=현행 QEMU, cutf_cvm=Cuttlefish, vbox86=Genymotion
        lowerHardware == "goldfish" ||
        lowerHardware == "ranchu" ||
        lowerHardware.startsWith("cutf") ||
        lowerHardware.startsWith("vbox86") ||
        lowerModel.contains("emulator") ||
        lowerModel.contains("android sdk built for")
}

/** [Build] 값을 읽어 [resolveApiBaseUrl]에 넘기기만 하는 어댑터. 판단 로직은 여기 두지 않는다. */
actual fun platformApiBaseUrl(): String = resolveApiBaseUrl(
    // 값은 빌드가 생성한다(app/shared/build.gradle.kts의 generateApiConfig).
    // 오버라이드가 없으면 빈 문자열 = "실행 시점에 알아서 고르라"는 뜻이다.
    configured = ANDROID_API_BASE_URL,
    fingerprint = Build.FINGERPRINT,
    product = Build.PRODUCT,
    hardware = Build.HARDWARE,
    model = Build.MODEL
)
