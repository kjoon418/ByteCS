package watson.bytecs.common.error

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import watson.bytecs.problem.domain.ProblemNotFoundException

/**
 * 애플리케이션이 예외를 처리하는 지점을 하나로 통일한다.
 * 정렬 순서: (1) Exception, (2) 커스텀 예외, (3) 표준 예외, (4) 프레임워크 예외.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handleInternalServerError(exception: Exception): ResponseEntity<ErrorResponse> {
        log.error("[Internal Server Error]", exception)

        val response = ErrorResponse("예상하지 못한 예외가 발생했습니다.", ErrorCode.INTERNAL_SERVER_ERROR)
        return ResponseEntity.internalServerError().body(response)
    }

    @ExceptionHandler(ProblemNotFoundException::class)
    fun handleProblemNotFound(exception: ProblemNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "리소스를 찾을 수 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.warn("[Invalid Input]", exception)

        val response = ErrorResponse(exception.message ?: "잘못된 입력입니다.", ErrorCode.INVALID_INPUT)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.warn("[Invalid Request]", exception)

        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "요청 형식이 올바르지 않습니다."
        val response = ErrorResponse(message, ErrorCode.INVALID_REQUEST)
        return ResponseEntity.badRequest().body(response)
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingPathVariableException::class,
    )
    fun handleMalformedRequest(exception: Exception): ResponseEntity<ErrorResponse> {
        log.warn("[Invalid Request]", exception)

        val response = ErrorResponse("요청 형식이 올바르지 않습니다.", ErrorCode.INVALID_REQUEST)
        return ResponseEntity.badRequest().body(response)
    }
}
