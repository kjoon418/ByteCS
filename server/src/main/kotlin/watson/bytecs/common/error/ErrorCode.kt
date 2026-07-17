package watson.bytecs.common.error

/**
 * HTTP 상태 코드와 별도로 관리되는 커스텀 상태 코드.
 * 클라이언트가 상태 코드만으로 예외 상황을 분기 처리할 수 있게 한다.
 */
enum class ErrorCode {
    INTERNAL_SERVER_ERROR,
    INVALID_REQUEST,
    INVALID_INPUT,
    PROBLEM_NOT_FOUND,

    UNAUTHORIZED,
    FORBIDDEN,
    CONFLICT,
    INVALID_CREDENTIALS,
    EMAIL_DUPLICATED,
    USER_NOT_FOUND,
    INVALID_USER_STATE,

    SESSION_NOT_FOUND,
    SESSION_ALREADY_COMPLETED,
    ITEM_NOT_VIEWABLE,

    SCRAP_NOT_FOUND,
}
