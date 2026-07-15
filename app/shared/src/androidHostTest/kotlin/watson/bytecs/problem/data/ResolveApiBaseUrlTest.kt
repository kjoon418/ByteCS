package watson.bytecs.problem.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [resolveApiBaseUrl]의 판정 규칙을 잠근다.
 *
 * 각 감지 신호는 **자기 필드만** 에뮬레이터 값으로 두고 나머지는 실기기 값으로 채워 검증한다.
 * 그래야 신호를 하나 지웠을 때 해당 테스트만 정확히 깨지고, OR 사슬에 무임승차하는 신호가 없음이 보장된다.
 */
class ResolveApiBaseUrlTest {

    // 실제 갤럭시 계열 기기의 형태를 본뜬 값. 어떤 감지 신호에도 걸리지 않아야 한다.
    private val realFingerprint = "samsung/dm3qksx/dm3q:14/UP1A.231005.007/S918NKSU3CXG1:user/release-keys"
    private val realProduct = "dm3qksx"
    private val realHardware = "exynos"
    private val realModel = "SM-S918N"

    private fun resolveOnRealDevice(
        configured: String = "",
        fingerprint: String = realFingerprint,
        product: String = realProduct,
        hardware: String = realHardware,
        model: String = realModel
    ): String = resolveApiBaseUrl(configured, fingerprint, product, hardware, model)

    @Test
    fun `오버라이드가 있으면 기기를 보지 않고 그대로 쓴다`() {
        val overridden = resolveApiBaseUrl(
            configured = "http://192.168.0.10:8080",
            // 에뮬레이터 신호를 전부 켜 둬도 오버라이드가 이겨야 한다.
            fingerprint = "generic/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036/11228894:userdebug/dev-keys",
            product = "sdk_gphone64_x86_64",
            hardware = "ranchu",
            model = "sdk_gphone64_x86_64"
        )

        assertEquals("http://192.168.0.10:8080", overridden)
    }

    @Test
    fun `오버라이드는 실기기에서도 그대로 쓴다`() {
        assertEquals(
            "https://api.bytecs.example.com",
            resolveOnRealDevice(configured = "https://api.bytecs.example.com")
        )
    }

    @Test
    fun `공백뿐인 오버라이드는 지정이 없는 것으로 본다`() {
        // 실수로 `bytecs.apiBaseUrl= ` 처럼 두면 빈 URL로 앱이 죽는 대신 자동 감지로 떨어져야 한다.
        assertEquals("http://localhost:8080", resolveOnRealDevice(configured = "   "))
    }

    @Test
    fun `오버라이드가 없는 실기기는 adb reverse 터널을 본다`() {
        assertEquals("http://localhost:8080", resolveOnRealDevice())
    }

    @Test
    fun `이 프로젝트가 쓰는 에뮬레이터의 실제 값으로 에뮬레이터로 판정한다`() {
        // Android Studio의 Small_Phone 이미지에서 실제로 관측한 값.
        val resolved = resolveApiBaseUrl(
            configured = "",
            fingerprint = "google/sdk_gphone64_x86_64/emu64xa:14/UE1A.230829.036/11228894:userdebug/dev-keys",
            product = "sdk_gphone64_x86_64",
            hardware = "ranchu",
            model = "sdk_gphone64_x86_64"
        )

        assertEquals("http://10.0.2.2:8080", resolved)
    }

    // --- 아래부터: 신호 하나씩만 켜고 나머지는 실기기 값 ---

    @Test
    fun `fingerprint가 generic으로 시작하면 에뮬레이터다`() {
        assertEquals(
            "http://10.0.2.2:8080",
            resolveOnRealDevice(fingerprint = "generic/vbox86p/vbox86p:9/PI/eng.buildbot:userdebug/test-keys")
        )
    }

    @Test
    fun `fingerprint가 unknown으로 시작하면 에뮬레이터다`() {
        assertEquals(
            "http://10.0.2.2:8080",
            resolveOnRealDevice(fingerprint = "unknown/full_x86/x86:8.0.0/OSR1.170901.043/1:userdebug/test-keys")
        )
    }

    @Test
    fun `fingerprint에 emulator가 들어가면 에뮬레이터다`() {
        assertEquals(
            "http://10.0.2.2:8080",
            resolveOnRealDevice(fingerprint = "Android/aosp_x86/emulator_x86:9/PSR1.180720.075/1:eng/test-keys")
        )
    }

    @Test
    fun `product에 sdk가 들어가면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(product = "sdk_gphone64_x86_64"))
    }

    @Test
    fun `product에 emulator가 들어가면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(product = "emulator_x86_64"))
    }

    @Test
    fun `hardware가 ranchu면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(hardware = "ranchu"))
    }

    @Test
    fun `hardware가 goldfish면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(hardware = "goldfish"))
    }

    @Test
    fun `hardware가 cuttlefish면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(hardware = "cutf_cvm"))
    }

    @Test
    fun `hardware가 genymotion이면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(hardware = "vbox86"))
    }

    @Test
    fun `model에 Emulator가 들어가면 에뮬레이터다`() {
        // "Android SDK built for"를 일부러 피한다. 그 문자열이 들어가면 바로 아래 신호에도 걸려
        // 이 테스트가 그쪽에 무임승차하고, 정작 이 신호를 지워도 깨지지 않는다.
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(model = "Genymotion Emulator"))
    }

    @Test
    fun `model이 Android SDK built for면 에뮬레이터다`() {
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(model = "Android SDK built for x86"))
    }

    @Test
    fun `대소문자가 달라도 같은 신호로 잡는다`() {
        // Build.* 값의 대소문자는 이미지마다 제각각이라 정규화에 기대는 부분을 잠근다.
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(hardware = "Ranchu"))
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(product = "SDK_gphone64"))
        assertEquals("http://10.0.2.2:8080", resolveOnRealDevice(fingerprint = "Generic/x/y:14/A/1:user/release-keys"))
    }
}
