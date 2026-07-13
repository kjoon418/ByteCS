package watson.bytecs.common.error

/**
 * 모든 에러 응답을 통일하기 위한 형태.
 * 클라이언트가 예외 응답을 일관된 형식으로 다룰 수 있게 한다.
 */
data class ErrorResponse(
    val message: String,
    val errorCode: ErrorCode,
)
