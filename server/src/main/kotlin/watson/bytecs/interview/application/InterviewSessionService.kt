package watson.bytecs.interview.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.interview.application.dto.InterviewAnswerResponse
import watson.bytecs.interview.application.dto.InterviewHintRevealResponse
import watson.bytecs.interview.application.dto.InterviewSessionResponse
import watson.bytecs.interview.application.dto.InterviewStatusResponse
import watson.bytecs.interview.domain.InterviewEligibility
import watson.bytecs.interview.domain.InterviewMemberOnlyException
import watson.bytecs.interview.domain.InterviewNoCandidateException
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.domain.InterviewQuotaExceededException
import watson.bytecs.interview.domain.InterviewReadiness
import watson.bytecs.interview.domain.InterviewReadinessStatus
import watson.bytecs.interview.domain.InterviewSession
import watson.bytecs.interview.domain.InterviewSessionAlreadyCompletedException
import watson.bytecs.interview.domain.InterviewSessionNotFoundException
import watson.bytecs.interview.domain.ExplanationJudge
import watson.bytecs.interview.infrastructure.InterviewPolicyProperties
import watson.bytecs.interview.infrastructure.InterviewPromptRepository
import watson.bytecs.interview.infrastructure.InterviewReadinessRepository
import watson.bytecs.interview.infrastructure.InterviewSessionRepository
import watson.bytecs.review.infrastructure.ConceptMasteryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * 면접 세션(계획 §3.3 — C2)을 조율한다. 승급 후보 산정·쿼터·준비도 갱신·복습 당김(DI11)·스트릭 OR(DI5)을 조합하고,
 * 채점 자체는 [ExplanationJudge]에 위임한다(로컬·테스트는 결정적 Fake — [watson.bytecs.interview.infrastructure.FakeExplanationJudge]).
 * 일반 세션([watson.bytecs.session.application.SessionService])과 같은 관례: 조회 기본 읽기 전용, 생성·갱신만 쓰기 트랜잭션.
 *
 * [review-todo] 이 슬라이스는 프로토타입 우선으로 구현했다 — 커버되지 않은 경계(동시 제출 경합, InterviewPrompt 부재 등
 * 데이터 정합성 가정)와 컨트롤러 통합 테스트는 별도 검토가 필요하다. 자세한 목록은 세션 핸드오프 메모리 참고.
 */
@Service
@Transactional(readOnly = true)
class InterviewSessionService(
    private val interviewSessionRepository: InterviewSessionRepository,
    private val interviewPromptRepository: InterviewPromptRepository,
    private val interviewReadinessRepository: InterviewReadinessRepository,
    private val conceptMasteryRepository: ConceptMasteryRepository,
    private val userRepository: UserRepository,
    private val explanationJudge: ExplanationJudge,
    private val responseMapper: InterviewResponseMapper,
    private val policy: InterviewPolicyProperties,
    private val clock: Clock,
) {

    /**
     * 홈 카드의 단일 출처. 게스트도 후보 수를 계산해 돌려준다(가입 유도 문구용).
     * 잔여 쿼터는 면접을 **이용할 수 있는** 사용자(회원, 또는 회원 전용이 풀린 테스터에선 게스트도)에게만 실제 값을 주고,
     * 이용할 수 없으면 0이다 — 클라 홈 카드는 이 값(>0)으로 진입 CTA 노출 여부를 판단한다(가입 유도 vs 진입).
     */
    fun getStatus(userId: Long): InterviewStatusResponse {
        val user = loadUser(userId)
        val candidateCount = selectCandidatePromptsOrdered(userId).size
        val remainingQuota = if (canUseInterview(user)) remainingQuotaToday(userId) else 0
        return InterviewStatusResponse(
            candidateConceptCount = candidateCount,
            remainingQuota = remainingQuota,
            isGuest = !user.isMember,
        )
    }

    /**
     * 오늘의 면접 세션을 시작한다.
     *  - 오늘 최신 세션이 진행 중이면 새로 만들지 않고 그대로 돌려준다(재개, 쿼터 미차감).
     *  - 그 외엔 오늘 쿼터를 확인하고(채점 성공 세션만 차감 대상), 승급 후보로 새 세션을 만든다.
     */
    @Transactional
    fun createTodaySession(userId: Long): InterviewSessionResponse {
        lockUser(userId)
        val user = loadUser(userId)
        requireMember(user)

        val today = today()
        val latest = findLatestToday(userId, today)
        if (latest != null && !latest.isCompleted) {
            return toSessionResponse(latest)
        }
        if (interviewSessionRepository.countGradedSessionsOn(userId, today) >= policy.dailyQuota) {
            throw InterviewQuotaExceededException.forToday()
        }

        val candidates = selectCandidatePromptsOrdered(userId)
        if (candidates.isEmpty()) {
            throw InterviewNoCandidateException.noEligibleConcept()
        }

        val session = InterviewSession.assign(userId, today, candidates.take(SESSION_SIZE).map { it.id })
        interviewSessionRepository.save(session)
        return toSessionResponse(session)
    }

    /** 오늘 진행 중인 면접 세션을 조회한다(중단·재개). 없으면 404 — 시작은 [createTodaySession]으로만 한다. */
    fun getTodaySession(userId: Long): InterviewSessionResponse {
        requireMember(loadUser(userId))
        val session = findLatestToday(userId, today()) ?: throw InterviewSessionNotFoundException.forToday()
        return toSessionResponse(session)
    }

    /**
     * 현재 질문의 힌트를 하나 더 공개한다(약→강, 학습 기록으로 영속). 채점·준비도·쿼터에 영향을 주지 않는다(무낙인).
     * 더블탭·경쟁 안전은 도메인이 판단한다 — 클라가 아는 공개 수와 실제가 일치할 때만 +1.
     * 힌트가 없거나 전부 공개했으면 증가 없이 현재 상태를 돌려준다. SessionService.revealHint와 동일 관례.
     */
    @Transactional
    fun revealHint(userId: Long, expectedRevealedCount: Int): InterviewHintRevealResponse {
        requireMember(loadUser(userId))
        val session = findLatestToday(userId, today()) ?: throw InterviewSessionNotFoundException.forToday()
        // 완료된 세션엔 힌트를 열 현재 질문이 없다(답 제출과 같은 의미의 예외로 일관되게 막는다).
        val promptId = session.currentPromptId() ?: throw InterviewSessionAlreadyCompletedException.forHintReveal()
        val prompt = loadPrompt(promptId)

        val revealedHintCount = session.revealHint(expectedRevealedCount, prompt.hintCount)
        return responseMapper.toHintRevealResponse(prompt, revealedHintCount)
    }

    /**
     * 현재 질문에 자기 말로 쓴 설명을 제출하고 채점한다.
     * 채점 성공 시 준비도를 갱신하고, '검증됨' 미달이면 그 개념의 복습 시점을 당긴다(DI11 — 당김 전용, 레벨 무변경).
     * 채점 실패(폴백) 시 준비도·복습은 손대지 않는다. 이 제출로 세션이 완료되면 스트릭을 기록한다(DI5 — 하루 멱등은 StudyStreak가 보장).
     */
    @Transactional
    fun submitAnswer(userId: Long, explanation: String): InterviewAnswerResponse {
        val user = loadUser(userId)
        requireMember(user)
        val session = findLatestToday(userId, today()) ?: throw InterviewSessionNotFoundException.forToday()
        val promptId = session.currentPromptId() ?: throw InterviewSessionAlreadyCompletedException.forAnswer()
        val prompt = loadPrompt(promptId)

        val judgeResult = explanationJudge.judge(prompt.rubricPoints, explanation)
        val judgedAt = Instant.now(clock)
        // '검증됨' 미달 개념의 '그때 푼 문제 다시 보기'(DI10) 대상 — 검증됨·폴백이면 null(그때는 재열람 블록을 그리지 않는다).
        var reviewProblemId: Long? = null
        if (judgeResult != null) {
            session.recordGraded(explanation, judgeResult.satisfiedPoints, judgeResult.comment, judgedAt)
            updateReadiness(userId, prompt.concept.id, judgeResult.satisfiedPoints, judgedAt)
            val allSatisfied = judgeResult.satisfiedPoints.all { it }
            if (!allSatisfied) {
                // '검증됨' 미달 — 그 개념의 숙련도 행에서 복습 시점을 당기고(DI11), 그때 푼 문제를 재열람 대상으로 싣는다(DI10).
                val mastery = conceptMasteryRepository.findByUserIdAndConceptId(userId, prompt.concept.id)
                mastery?.pullReviewDateForward(today().plusDays(REVIEW_PULL_FORWARD_DAYS))
                reviewProblemId = mastery?.lastProblemId
            }
        } else {
            session.recordFallback(explanation, judgedAt)
        }

        val streak = if (session.isCompleted) {
            user.recordStudy(today())
            user.streak
        } else {
            null
        }

        val nextPrompt = session.currentPromptId()?.let { loadPrompt(it) }
        return responseMapper.toAnswerResponse(session, prompt, judgeResult, nextPrompt, streak, reviewProblemId)
    }

    /**
     * 승급 후보 개념(레벨≥1 ∧ 승인된 면접 질문 존재)의 면접 질문을, 준비도 우선순위(미검증>부분>검증됨)·개념 id 순으로 돌려준다.
     * 개념당 면접 질문은 1차 기본 1개이되, 여럿이면 id가 가장 작은 것으로 결정적으로 고른다.
     */
    private fun selectCandidatePromptsOrdered(userId: Long): List<InterviewPrompt> {
        val masteredConceptIds =
            conceptMasteryRepository
                .findConceptIdsByUserIdAndLevelGreaterThanEqual(userId, InterviewEligibility.MASTERY_LEVEL).toSet()
        if (masteredConceptIds.isEmpty()) {
            return emptyList()
        }

        val promptByConceptId = interviewPromptRepository.findApproved()
            .filter { it.concept.id in masteredConceptIds }
            .groupBy { it.concept.id }
            .mapValues { (_, prompts) -> prompts.minBy { it.id } }
        if (promptByConceptId.isEmpty()) {
            return emptyList()
        }

        val readinessByConceptId = interviewReadinessRepository
            .findByUserIdAndConceptIdIn(userId, promptByConceptId.keys)
            .associateBy { it.conceptId }

        return promptByConceptId.keys
            .sortedWith(compareBy({ priorityOf(readinessByConceptId[it]?.status) }, { it }))
            .map { promptByConceptId.getValue(it) }
    }

    private fun priorityOf(status: InterviewReadinessStatus?): Int = when (status) {
        null, InterviewReadinessStatus.UNVERIFIED -> 0
        InterviewReadinessStatus.PARTIAL -> 1
        InterviewReadinessStatus.VERIFIED -> 2
    }

    private fun updateReadiness(userId: Long, conceptId: Long, satisfiedPoints: List<Boolean>, updatedAt: Instant) {
        val readiness = interviewReadinessRepository.findByUserIdAndConceptId(userId, conceptId)
            ?: InterviewReadiness.initial(userId, conceptId)
        // applyResult로 상태·updatedAt(NOT NULL)을 채운 뒤에 저장한다 — 신규 행을 채우기 전에 먼저 save하면
        // updatedAt(lateinit) 미초기화 상태로 INSERT돼 제약 위반이 난다(실서버 스모크로 발견).
        readiness.applyResult(satisfiedPoints.count { it }, satisfiedPoints.size, updatedAt)
        interviewReadinessRepository.save(readiness)
    }

    /** 면접 세션을 이용할 수 있는지 — 회원이거나, 회원 전용이 풀린 프로파일(테스터)이면 게스트도 가능. */
    private fun canUseInterview(user: User): Boolean = user.isMember || !policy.memberOnly

    /** 회원 전용 게이트. 이용 불가(회원 전용인데 게스트)면 거부한다. 테스터에선 [InterviewPolicyProperties.memberOnly]=false라 통과한다. */
    private fun requireMember(user: User) {
        if (!canUseInterview(user)) {
            throw InterviewMemberOnlyException.forGuest()
        }
    }

    /** 오늘 남은 세션 쿼터(채점 성공 세션 기준). 사실상 무제한이면 큰 값이 그대로 나온다(테스터). */
    private fun remainingQuotaToday(userId: Long): Int =
        (policy.dailyQuota - interviewSessionRepository.countGradedSessionsOn(userId, today())).coerceAtLeast(0).toInt()

    private fun toSessionResponse(session: InterviewSession): InterviewSessionResponse {
        val currentPrompt = session.currentPromptId()?.let { loadPrompt(it) }
        return responseMapper.toSessionResponse(session, currentPrompt)
    }

    /** 세션 생성 경합을 사용자 행 비관적 잠금으로 직렬화한다(SessionService.lockUser와 같은 관례). */
    private fun lockUser(userId: Long) {
        userRepository.findWithLockById(userId).orElseThrow { UserNotFoundException.byId(userId) }
    }

    private fun findLatestToday(userId: Long, today: LocalDate): InterviewSession? =
        interviewSessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(userId, today)

    private fun loadUser(userId: Long): User =
        userRepository.findById(userId).orElseThrow { UserNotFoundException.byId(userId) }

    private fun loadPrompt(promptId: Long): InterviewPrompt =
        interviewPromptRepository.findById(promptId).orElseThrow {
            IllegalStateException("면접 질문을 찾을 수 없습니다. id = $promptId")
        }

    private fun today(): LocalDate = LocalDate.now(clock)

    companion object {
        // 회원 전용·하루 쿼터는 InterviewPolicyProperties로 옮겨(환경별 완화 가능), 세션 크기·복습 당김만 상수로 둔다.
        private const val SESSION_SIZE = 3
        // 승급 임계(DI8)는 정답 시 신규 승급 판정(InterviewUnlockCalculator)과 공유하도록 InterviewEligibility.MASTERY_LEVEL로 단일화.
        private const val REVIEW_PULL_FORWARD_DAYS = 1L // DI11: 면접일+1일
    }
}
