package watson.bytecs.common.error

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
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
import watson.bytecs.interview.domain.InterviewMemberOnlyException
import watson.bytecs.interview.domain.InterviewNoCandidateException
import watson.bytecs.interview.domain.InterviewQuotaExceededException
import watson.bytecs.interview.domain.InterviewSessionAlreadyCompletedException
import watson.bytecs.interview.domain.InterviewSessionNotFoundException
import watson.bytecs.problem.domain.InvalidApprovalStateException
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.scrap.domain.ScrapNotFoundException
import watson.bytecs.session.domain.ItemNotViewableException
import watson.bytecs.session.domain.SessionAlreadyCompletedException
import watson.bytecs.session.domain.SessionNotFoundException

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

    @ExceptionHandler(SessionNotFoundException::class)
    fun handleSessionNotFound(exception: SessionNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "세션을 찾을 수 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(ScrapNotFoundException::class)
    fun handleScrapNotFound(exception: ScrapNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "스크랩을 찾을 수 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(SessionAlreadyCompletedException::class)
    fun handleSessionAlreadyCompleted(exception: SessionAlreadyCompletedException): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "이미 완료된 세션입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(ItemNotViewableException::class)
    fun handleItemNotViewable(exception: ItemNotViewableException): ResponseEntity<ErrorResponse> {
        log.warn("[Forbidden] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "아직 볼 수 없는 문제입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }

    @ExceptionHandler(InvalidApprovalStateException::class)
    fun handleInvalidApprovalState(exception: InvalidApprovalStateException): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "허용되지 않는 승인 상태 전이입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(InterviewMemberOnlyException::class)
    fun handleInterviewMemberOnly(exception: InterviewMemberOnlyException): ResponseEntity<ErrorResponse> {
        log.warn("[Forbidden] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "면접 세션은 회원만 이용할 수 있습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response)
    }

    @ExceptionHandler(InterviewQuotaExceededException::class)
    fun handleInterviewQuotaExceeded(exception: InterviewQuotaExceededException): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "오늘의 면접 세션을 모두 사용했습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(InterviewNoCandidateException::class)
    fun handleInterviewNoCandidate(exception: InterviewNoCandidateException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "면접 연습을 시작할 개념이 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(InterviewSessionNotFoundException::class)
    fun handleInterviewSessionNotFound(exception: InterviewSessionNotFoundException): ResponseEntity<ErrorResponse> {
        log.warn("[Not Found] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "면접 세션을 찾을 수 없습니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(InterviewSessionAlreadyCompletedException::class)
    fun handleInterviewSessionAlreadyCompleted(
        exception: InterviewSessionAlreadyCompletedException,
    ): ResponseEntity<ErrorResponse> {
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse(exception.message ?: "이미 완료된 면접 세션입니다.", exception.errorCode)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        // unique 제약 등 무결성 위반이 최종 방어선이 될 때 500 대신 409로 매핑한다.
        // 어떤 제약인지 단정하지 않는 '중립' 충돌로 응답한다(이메일 중복 같은 도메인 의미 부여는 각 서비스가 책임진다).
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse("요청을 처리할 수 없습니다.", ErrorCode.CONFLICT)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response)
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(exception: ObjectOptimisticLockingFailureException): ResponseEntity<ErrorResponse> {
        // 같은 자원에 대한 동시 갱신 경합(버전 불일치). 조용히 덮어써 상태를 망가뜨리는 대신 409로 되돌려, 클라이언트가 재시도하게 한다.
        log.warn("[Conflict] {}", exception.message)

        val response = ErrorResponse("다른 요청과 충돌했습니다. 다시 시도해 주세요.", ErrorCode.CONFLICT)
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
