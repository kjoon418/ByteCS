package watson.bytecs.study.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.ProblemCategory
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.session.infrastructure.SessionRepository
import watson.bytecs.study.LearningHistory
import watson.bytecs.study.application.dto.CategoryHistoryResponse

/**
 * 카테고리별 학습 이력을 조율한다(명세 §7 '카테고리별 학습 이력').
 * '푼 문제'는 [LearningHistory]가 정의하는 세션 풀이 이력을 그대로 쓴다 — 세션 배정 선정과 같은 출처를 공유해
 * '푼 문제'의 정의가 배정과 조회에서 어긋나지 않는다(D6·D9 일원화 이후 세션 단독 출처).
 */
@Service
@Transactional(readOnly = true)
class CategoryHistoryService(
    private val learningHistory: LearningHistory,
    private val problemRepository: ProblemRepository,
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val responseMapper: CategoryHistoryResponseMapper,
) {

    /**
     * 본인이 푼 문제를 8개 고정 대분류로 그룹핑해 돌려준다. 삭제된 사용자의 토큰은 404다(스크랩·세션과 같은 사용자 격리 규약).
     * 8분류 전체를 항상 반환하며([ProblemCategory] 선언 순서), 문제가 없는 카테고리는 items가 빈 목록이다('준비 중').
     * 대표 개념이 아직 미분류(category=null)인 문제는 어느 그룹에도 실리지 않는다 — 백필 전 안전한 퇴화(명세 §7 백필 참고).
     */
    fun findByCategory(userId: Long): List<CategoryHistoryResponse> {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException.byId(userId)
        }

        val solvedProblemIds = learningHistory.findSolvedProblemIds(userId)
        // 빈 id 목록으로 in절을 쏘지 않고 바로 반환한다(불필요한 쿼리 방지 — Spring Data findAllById와 같은 최적화).
        val solvedProblemsByCategory = if (solvedProblemIds.isEmpty()) {
            emptyMap()
        } else {
            problemRepository.findAllByIdWithConceptsAndEnrichment(solvedProblemIds)
                .groupBy { it.representativeCategory() }
        }
        val submittedAnswersByProblemId = sessionRepository.findSolvedItemAnswers(userId)
            .associate { it.problemId to it.submittedAnswer }

        return ProblemCategory.entries.map { category ->
            responseMapper.toGroupResponse(
                category,
                solvedProblemsByCategory[category].orEmpty(),
                submittedAnswersByProblemId,
            )
        }
    }
}
