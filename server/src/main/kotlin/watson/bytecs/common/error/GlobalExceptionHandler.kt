package watson.bytecs.common.error

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingPathVariableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import watson.bytecs.account.domain.EmailDuplicatedException
import watson.bytecs.account.domain.InvalidCredentialsException
import watson.bytecs.account.domain.InvalidUserStateException
import watson.bytecs.account.domain.UserNotFoundException
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

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(exception: UserNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "사용자를 찾을 수 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(EmailDuplicatedException::class)
    fun handleEmailDuplicated(exception: EmailDuplicatedException): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "이미 사용 중인 이메일입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(InvalidUserStateException::class)
    fun handleInvalidUserState(exception: InvalidUserStateException): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "허용되지 않는 사용자 상태 전이입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(exception: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        log.warn("[Unauthorized] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "이메일 또는 비밀번호가 올바르지 않습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        // 이메일 중복 사전 검사와 저장 사이의 경합(TOCTOU)으로 unique 제약이 최종 방어선이 될 때, 500 대신 409로 매핑한다.
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse("이미 사용 중인 이메일입니다.", ErrorCode.EMAIL_DUPLICATED)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
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
