package watson.bytecs

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform