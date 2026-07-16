package watson.bytecs.report.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.report.application.dto.ReportResponse
import watson.bytecs.report.domain.ContentReport
import watson.bytecs.report.domain.ReportCategory
import watson.bytecs.report.infrastructure.ContentReportRepository
import java.time.Clock
import java.time.Instant

/**
 * 콘텐츠 오류 신고 접수를 조율한다.
 * 신고 대상은 서빙된 콘텐츠여야 하므로 문제 존재를 먼저 확인하고(없으면 404), 접수 시각은 주입된 [Clock]으로 결정적으로 찍는다.
 */
@Service
@Transactional(readOnly = true)
class ReportService(
    private val contentReportRepository: ContentReportRepository,
    private val problemRepository: ProblemRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    /**
     * 콘텐츠 오류 신고를 접수한다.
     * 스테이트리스 JWT는 계정 삭제 후에도 유효하므로, 삭제된 사용자의 토큰이 이 경로로 고아 신고 행을 만들지 않도록
     * 저장 전에 사용자 존재를 확인한다(없으면 404 UserNotFound).
     */
    @Transactional
    fun report(userId: Long, problemId: Long, category: ReportCategory, message: String?): ReportResponse {
        requireUserExists(userId)
        if (!problemRepository.existsById(problemId)) {
            throw ProblemNotFoundException.byId(problemId)
        }

        val saved = contentReportRepository.save(
            ContentReport(
                userId = userId,
                problemId = problemId,
                category = category,
                // 상세 내용은 선택이라 공백은 '없음'(null)으로 정규화해 저장 데이터를 깨끗이 둔다.
                message = message?.takeIf { it.isNotBlank() },
                createdAt = Instant.now(clock),
            ),
        )
        return ReportResponse(
            id = saved.id,
            problemId = saved.problemId,
            category = saved.category.name,
            createdAt = saved.createdAt,
        )
    }

    private fun requireUserExists(userId: Long) {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException.byId(userId)
        }
    }
}
