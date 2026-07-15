package watson.bytecs.scrap.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.scrap.application.dto.ScrapDetailResponse
import watson.bytecs.scrap.application.dto.ScrapSummaryResponse
import watson.bytecs.scrap.domain.Scrap
import watson.bytecs.scrap.domain.ScrapNotFoundException
import watson.bytecs.scrap.infrastructure.ScrapRepository
import java.time.Clock
import java.time.Instant

/**
 * 문제 스크랩(개인 북마크)을 조율한다.
 * 스크랩은 사용자 소유·격리이며, 모든 조회·삭제는 principal(userId)로만 결정해 타인의 스크랩에 접근할 수 없게 한다.
 * 토글은 멱등하다 — 중복 스크랩은 no-op, 없는 스크랩 해제도 no-op.
 * 기본 읽기 전용이되, 상태를 바꾸는 메서드만 쓰기 트랜잭션으로 재정의한다.
 */
@Service
@Transactional(readOnly = true)
class ScrapService(
    private val scrapRepository: ScrapRepository,
    private val problemRepository: ProblemRepository,
    private val responseMapper: ScrapResponseMapper,
    private val clock: Clock,
) {

    /** 문제를 스크랩한다(멱등). 이미 스크랩했으면 아무것도 하지 않는다. 없는 문제는 스크랩할 수 없다(404). */
    @Transactional
    fun scrap(userId: Long, problemId: Long) {
        if (!problemRepository.existsById(problemId)) {
            throw ProblemNotFoundException.byId(problemId)
        }
        if (scrapRepository.existsByUserIdAndProblemId(userId, problemId)) {
            return
        }
        try {
            scrapRepository.save(Scrap(userId = userId, problemId = problemId, createdAt = Instant.now(clock)))
        } catch (e: DataIntegrityViolationException) {
            // 사전 검사와 저장 사이의 경합으로 (user_id, problem_id) 유니크가 최종 방어선이 될 때도 멱등하게 흡수한다(이미 스크랩됨).
        }
    }

    /** 스크랩을 해제한다(멱등). 스크랩이 없으면 아무것도 하지 않는다. */
    @Transactional
    fun unscrap(userId: Long, problemId: Long) {
        scrapRepository.deleteByUserIdAndProblemId(userId, problemId)
    }

    /** 본인 스크랩 목록을 최신순으로 돌려준다(사용자 격리). 회수·삭제된 문제는 question이 null로 나온다. */
    fun list(userId: Long): List<ScrapSummaryResponse> {
        val scraps = scrapRepository.findByUserIdOrderByCreatedAtDesc(userId)
        val problemsById = problemRepository.findAllById(scraps.map { it.problemId })
            .associateBy(Problem::id)
        return scraps.map { responseMapper.toSummaryResponse(it, problemsById[it.problemId]) }
    }

    /**
     * 스크랩한 문제를 읽기 전용으로 재열람한다(문제·모범답안·해설).
     * 본인이 스크랩하지 않은 문제는 404다(타인 스크랩·미스크랩 문제의 존재 여부를 흘리지 않는 사용자 격리).
     */
    fun detail(userId: Long, problemId: Long): ScrapDetailResponse {
        val scrap = scrapRepository.findByUserIdAndProblemId(userId, problemId)
            ?: throw ScrapNotFoundException.forProblem(problemId)
        val problem = problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }
        return responseMapper.toDetailResponse(scrap, problem)
    }
}
