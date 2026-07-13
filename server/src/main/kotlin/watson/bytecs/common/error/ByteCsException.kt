package watson.bytecs.common.error

/**
 * 애플리케이션 내부에서 사용하는 커스텀 예외의 기반 타입.
 * 커스텀 상태 코드를 함께 실어, 전역 핸들러가 일관된 응답으로 변환할 수 있게 한다.
 */
abstract class ByteCsException(
    val errorCode: ErrorCode,
    message: String,
) : RuntimeException(message)
