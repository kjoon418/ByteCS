package watson.bytecs.report.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.report.application.ReportService
import watson.bytecs.report.application.dto.ReportResponse
import watson.bytecs.report.presentation.request.CreateReportRequest

/**
 * 인증된 사용자가 서빙된 콘텐츠(문제)의 오류를 신고한다.
 * 신고자는 토큰에서 복원한 principal(userId)로만 결정한다. 이 경로는 SecurityConfig에서 인증을 명시적으로 요구한다
 * (문제 조회 permitAll보다 앞선 규칙) — 익명 신고를 막고 신고자를 기록하기 위함.
 */
@RestController
@RequestMapping("/api/problems/{problemId}/reports")
class ReportController(
    private val reportService: ReportService,
    private val requestMapper: ReportRequestMapper,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun report(
        @PathVariable problemId: Long,
        @Valid @RequestBody request: CreateReportRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ReportResponse =
        reportService.report(user.userId, problemId, requestMapper.toCategory(request), request.message)
}
