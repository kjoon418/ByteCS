package watson.bytecs

class WasmPlatform : Platform {
    override val name: String = "Web (Kotlin/Wasm)"
}

// 데모 잔재라 실사용처는 없지만, expect 선언이 있어 모든 타깃에 actual이 있어야 컴파일된다.
actual fun getPlatform(): Platform = WasmPlatform()
