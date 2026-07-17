package watson.bytecs.extrastudy.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.extrastudy.application.dto.ExtraStudyAttemptResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyCurrentResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyHintRevealResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyRevealResponse
import watson.bytecs.extrastudy.domain.ExtraStudy
import watson.bytecs.extrastudy.domain.ExtraStudyNoOpenItemException
import watson.bytecs.extrastudy.infrastructure.ExtraStudyRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.study.LearningHistory
import java.time.Clock
import java.time.LocalDate
import kotlin.random.Random

/**
 * 추가 학습(Extra Study)을 조율한다. 세션과 동일한 학습 데이터 의미론(풀이 이력·숙련도·복습)을 갖되,
 * 목표 분량·완결(완료 카운트·스트릭)이 없고 한 번에 한 문제만 열어 이어 풀게 한다.
 *
 * 판정은 콘텐츠(Problem.evaluate)에, 열린 항목·승격·공개 규칙은 애그리거트(ExtraStudy)에, 선정 통일은 [LearningHistory]에 위임하고,
 * 서비스는 사용자당 1행 get-or-create·선정·조회·응답 변환과 정답 통과 시 숙련도 갱신만 담당한다.
 * get-or-create의 INSERT는 [ExtraStudyCreator]가 REQUIRES_NEW로 격리하므로, 상태를 바꾸는 메서드만 쓰기 트랜잭션으로 둔다.
 */
@Service
@Transactional(readOnly = true)
class ExtraStudyService(
    private val extraStudyRepository: ExtraStudyRepository,
    private val problemRepository: ProblemRepository,
    private val reviewService: ReviewService,
    private val learningHistory: LearningHistory,
    private val responseMapper: ExtraStudyResponseMapper,
    private val extraStudyCreator: ExtraStudyCreator,
    private val clock: Clock,
    // 복습 도래·안 푼 후보 중 하나를 무작위로 뽑는 데 쓴다. 기본은 Random.Default이고, 테스트에서 시드 고정 Random을 주입한다.
    private val random: Random = Random.Default,
) {

    /**
     * 현재(이어 풀) 문제를 돌려주거나, 없으면 새로 뽑아 열린 항목으로 고정해 돌려준다. 뽑을 게 없으면 소진.
     *  - 열린 항목이 있으면 그대로 반환한다(재진입 이어 풀기 — 새로고침으로 다시 뽑지 않는다).
     *  - 없으면 [selectNext]로 1건 선정 → 열린 항목으로 저장 → 반환. 선정 결과가 없으면 exhausted=true(열린 항목을 만들지 않음).
     */
    @Transactional
    fun getCurrent(userId: Long): ExtraStudyCurrentResponse {
        val extraStudy = findOrCreate(userId)

        extraStudy.openProblemId()?.let { openId ->
            return responseMapper.toCurrentResponse(loadProblem(openId), extraStudy.openItem!!.revealedHintCount)
        }

        val nextProblemId = selectNext(userId) ?: return responseMapper.exhausted()
        extraStudy.assignOpen(nextProblemId)
        // 방금 REQUIRES_NEW로 만든 행은 이 트랜잭션에서 분리 상태일 수 있으므로 병합 저장으로 열린 항목을 확정한다.
        extraStudyRepository.save(extraStudy)
        return responseMapper.toCurrentResponse(loadProblem(nextProblemId), revealedHintCount = 0)
    }

    /**
     * 열린 문제에 답을 제출한다. 정답이면 solved로 승격하고 그 문제의 모든 개념 숙련도를 세션과 동일하게 갱신한다(recordSolve).
     * 불일치·근접은 무낙인으로 정상 응답하며(오답 횟수만 누적) 개념·정답을 노출하지 않는다. 열린 항목이 없으면 409.
     */
    @Transactional
    fun submitAnswer(userId: Long, answer: AnswerText): ExtraStudyAttemptResponse {
        val extraStudy = extraStudyRepository.findByUserId(userId)
            ?: throw ExtraStudyNoOpenItemException.forAttempt()
        val open = extraStudy.openItem
            ?: throw ExtraStudyNoOpenItemException.forAttempt()
        val problem = loadProblem(open.problemId)

        // 통과 직후 열린 항목이 비워지므로, 도움 신호(정답 공개·힌트·교정 힌트 여부)를 미리 스냅샷한다.
        val revealed = open.revealed
        val revealedHintCount = open.revealedHintCount
        val misconceptionHintSeen = open.misconceptionHintSeen

        val outcome = problem.evaluate(answer)
        extraStudy.recordAttempt(outcome.judgement, answer.value, misconceptionShown = outcome.misconceptionHint != null)

        // 정답이면 세션 제출과 완전히 같은 호출로 숙련도를 갱신한다(같은 커밋 경계, 결정적).
        if (outcome.judgement == Judgement.CORRECT) {
            val signal = MasterySignal.of(revealed, revealedHintCount, misconceptionHintSeen)
            reviewService.recordSolve(userId, problem.conceptIds(), signal, today(), problem.id)
        }

        return responseMapper.toAttemptResponse(outcome, problem)
    }

    /** 열린 문제의 모범답안을 공개한다(무낙인 안전판, 시도 전에도 허용). 열린 항목이 없으면 409. */
    @Transactional
    fun reveal(userId: Long): ExtraStudyRevealResponse {
        val extraStudy = extraStudyRepository.findByUserId(userId)
            ?: throw ExtraStudyNoOpenItemException.forReveal()
        extraStudy.reveal()

        val problemId = requireNotNull(extraStudy.openProblemId()) {
            "정답 공개가 성공했다면 열린 문제가 존재해야 한다."
        }
        return responseMapper.toRevealResponse(loadProblem(problemId))
    }

    /**
     * 열린 문제의 힌트를 하나 더 공개한다(약→강). 더블탭·경쟁 안전은 도메인이 판단한다(클라가 아는 공개 수와 일치할 때만 +1).
     * 열린 항목이 없으면 409.
     */
    @Transactional
    fun revealHint(userId: Long, expectedRevealedCount: Int): ExtraStudyHintRevealResponse {
        val extraStudy = extraStudyRepository.findByUserId(userId)
            ?: throw ExtraStudyNoOpenItemException.forHintReveal()
        val problemId = extraStudy.openProblemId()
            ?: throw ExtraStudyNoOpenItemException.forHintReveal()
        val problem = loadProblem(problemId)

        val revealedHintCount = extraStudy.revealHint(expectedRevealedCount, problem.hintCount)
        return responseMapper.toHintRevealResponse(problem, revealedHintCount)
    }

    /**
     * 다음 한 문제를 고른다(설계 §1.2, 오너 결정 2).
     *  ① 복습 시점이 도래한 개념의 문제 중 무작위 1건 → ② (없으면) 아직 풀지 않은 문제 중 무작위 1건.
     * 둘 다 없으면 소진(null). 전체 반복 폴백은 두지 않는다(같은 문제 무한 반복 방지·정착 신호 보존).
     * '푼 문제'·'배정 이력'은 세션 ∪ 추가 학습 합집합([LearningHistory])을 본다.
     */
    private fun selectNext(userId: Long): Long? {
        val allProblemIds = problemRepository.findAllIdsOrderByIdAsc()
        if (allProblemIds.isEmpty()) return null
        val poolIds = allProblemIds.toSet()

        val assignedProblemIds = learningHistory.findAssignedProblemIds(userId)
        val dueReviewProblemIds = reviewService.selectDueReviewProblemIds(userId, today(), assignedProblemIds, poolIds)
        if (dueReviewProblemIds.isNotEmpty()) return dueReviewProblemIds.random(random)

        val solvedProblemIds = learningHistory.findSolvedProblemIds(userId)
        val unseenProblemIds = allProblemIds.filter { it !in solvedProblemIds }
        if (unseenProblemIds.isNotEmpty()) return unseenProblemIds.random(random)

        return null
    }

    /**
     * 추가 학습 행을 찾거나, 없으면 만든다. 사용자당 1행 유니크 제약의 경합을 여기서 멱등하게 흡수한다(세션 findOrCreateToday와 같은 관례).
     * 생성은 [ExtraStudyCreator]가 REQUIRES_NEW로 격리하므로, 유니크 제약으로 실패해도 이 트랜잭션은 오염되지 않는다.
     */
    private fun findOrCreate(userId: Long): ExtraStudy {
        extraStudyRepository.findByUserId(userId)?.let { return it }

        return try {
            extraStudyCreator.createInNewTransaction(userId)
        } catch (e: DataIntegrityViolationException) {
            extraStudyRepository.findByUserId(userId) ?: throw e
        }
    }

    private fun loadProblem(problemId: Long): Problem =
        problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

    private fun today(): LocalDate = LocalDate.now(clock)
}
