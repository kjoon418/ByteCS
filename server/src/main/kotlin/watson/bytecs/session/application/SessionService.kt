package watson.bytecs.session.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import watson.bytecs.account.domain.User
import watson.bytecs.account.domain.UserNotFoundException
import watson.bytecs.account.infrastructure.UserRepository
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.problem.domain.ProblemNotFoundException
import watson.bytecs.problem.infrastructure.ProblemRepository
import watson.bytecs.review.application.ReviewService
import watson.bytecs.review.domain.MasterySignal
import watson.bytecs.session.application.dto.HintRevealResponse
import watson.bytecs.session.application.dto.PastItemResponse
import watson.bytecs.session.application.dto.RevealResponse
import watson.bytecs.session.application.dto.SessionAttemptResponse
import watson.bytecs.session.application.dto.SessionStateResponse
import watson.bytecs.session.domain.Session
import watson.bytecs.session.domain.SessionAlreadyCompletedException
import watson.bytecs.session.domain.SessionNotFoundException
import watson.bytecs.session.infrastructure.SessionRepository
import java.time.Clock
import java.time.LocalDate

/**
 * 오늘의 한입(일일 세션)을 조율한다.
 * 판정은 콘텐츠(Problem.judge)에, 진행·완료·정답 공개 규칙은 세션(Session)에, 스트릭은 사용자(User)에 위임하고,
 * 서비스는 하루 1세션 get-or-create·배정·조회·응답 변환만 담당한다.
 * 세션 생성은 오직 '오늘 조회(getOrCreateToday)'에서만 일어난다. 제출·공개·지난 문제는 이미 시작한 세션을 전제하며, 없으면 404다.
 * 기본 읽기 전용이되, 세션을 만들거나 상태를 바꾸는 메서드만 쓰기 트랜잭션으로 재정의한다.
 */
@Service
@Transactional(readOnly = true)
class SessionService(
    private val sessionRepository: SessionRepository,
    private val problemRepository: ProblemRepository,
    private val userRepository: UserRepository,
    private val responseMapper: SessionResponseMapper,
    private val sessionCreator: SessionCreator,
    private val reviewService: ReviewService,
    private val clock: Clock,
) {

    /**
     * 오늘의 세션을 가져오거나(없으면) 새로 배정해 시작 상태를 돌려준다. 하루 경계·중단 재개의 진입점이다.
     * 생성(INSERT)은 [SessionCreator]가 REQUIRES_NEW로 격리하므로, 이 메서드는 조회만 하는 읽기 전용 트랜잭션(클래스 기본)으로 둔다.
     */
    fun getOrCreateToday(userId: Long): SessionStateResponse {
        val session = findOrCreateToday(userId)
        val currentProblem = session.currentItemProblemId()?.let { loadProblem(it) }
        // 홈 화면이 로드 시점에 바로 스트릭을 보여주도록, 오늘 상태에 사용자의 현재 스트릭을 함께 싣는다.
        val streak = loadUser(userId).streak

        return responseMapper.toStateResponse(session, currentProblem, streak)
    }

    /**
     * 현재 본 문제에 답을 제출한다. 정답을 직접 맞혀야만 다음으로 진행되고, 마지막이면 세션이 완료되며 스트릭이 오른다.
     * 불일치·근접은 무낙인으로 정상 응답하며(오답 횟수만 누적) 개념·정답을 노출하지 않는다.
     */
    @Transactional
    fun submitAnswer(userId: Long, answer: AnswerText): SessionAttemptResponse {
        val session = loadTodayOrThrow(userId)
        // 완료된 세션엔 현재 본 문제가 없다. 도메인 규칙과 같은 의미의 예외로 일관되게 막는다(추가 연습 유도).
        val currentProblemId = session.currentItemProblemId()
            ?: throw SessionAlreadyCompletedException.forAttempt()
        val problem = loadProblem(currentProblemId)

        // 판정과 오답 교정 힌트를 함께 산출한다(교정 힌트 매칭 시 MISMATCH 확정 — 근접보다 우선). 진행에는 확정된 판정을 쓴다.
        // 정답으로 통과할 칸(= 진입 시점의 현재 칸)의 위치를 미리 잡아, 통과 직후 그 칸의 도움 신호를 읽는다.
        val solvedPosition = session.currentPosition
        val outcome = problem.evaluate(answer)
        session.recordAttempt(outcome.judgement, answer, misconceptionShown = outcome.misconceptionHint != null)

        // 정답이면 그 문제의 모든 개념 숙련도를 같은 트랜잭션에서 갱신한다(기능 3). 도움 신호는 방금 통과한 칸에서 파생한다.
        if (outcome.judgement == Judgement.CORRECT) {
            val solvedItem = session.items[solvedPosition]
            val signal = MasterySignal.of(
                revealed = solvedItem.revealed,
                revealedHintCount = solvedItem.revealedHintCount,
                misconceptionHintSeen = solvedItem.misconceptionHintSeen,
            )
            reviewService.recordSolve(userId, problem.conceptIds(), signal, today(), problem.id)
        }

        // 여기 도달 시점엔 진입 전 미완료가 보장되므로(currentItemProblemId != null), 지금 완료됐다면 '방금' 완료된 것이다.
        val streak = if (session.isCompleted) {
            val user = loadUser(userId)
            user.recordStudy(today())
            user.streak
        } else {
            null
        }

        val nextProblem = session.currentItemProblemId()?.let { loadProblem(it) }
        return responseMapper.toAttemptResponse(session, outcome, problem, nextProblem, streak)
    }

    /**
     * 현재 본 문제의 힌트를 하나 더 공개한다(약→강, 학습 기록으로 영속).
     * 더블탭·경쟁 안전은 도메인이 판단한다 — 클라가 아는 공개 수와 실제가 일치할 때만 +1.
     * 힌트가 없거나 전부 공개했으면 증가 없이 현재 상태를 돌려준다.
     */
    @Transactional
    fun revealHint(userId: Long, expectedRevealedCount: Int): HintRevealResponse {
        val session = loadTodayOrThrow(userId)
        // 완료된 세션엔 힌트를 열 현재 본 문제가 없다(정답 공개와 같은 의미의 예외로 일관되게 막는다).
        val currentProblemId = session.currentItemProblemId()
            ?: throw SessionAlreadyCompletedException.forHintReveal()
        val problem = loadProblem(currentProblemId)

        val revealedHintCount = session.revealHint(expectedRevealedCount, problem.hintCount)
        return responseMapper.toHintRevealResponse(problem, revealedHintCount)
    }

    /** 현재 본 문제의 모범답안을 공개한다(무낙인 안전판). 공개 가능 여부·완료 여부는 도메인이 판단한다. */
    @Transactional
    fun reveal(userId: Long): RevealResponse {
        val session = loadTodayOrThrow(userId)
        session.reveal() // 완료 세션 공개는 도메인이 막는다(시도 전 공개는 2026-07-17부터 허용).

        val problemId = requireNotNull(session.currentItemProblemId()) {
            "정답 공개가 성공했다면 현재 본 문제가 존재해야 한다."
        }
        return responseMapper.toRevealResponse(loadProblem(problemId))
    }

    /** 이미 지나온 본 문제를 읽기 전용으로 조회한다(진행·완료 카운트에 영향 없음). */
    fun getPastItem(userId: Long, position: Int): PastItemResponse {
        val session = loadTodayOrThrow(userId)
        val item = session.pastItemAt(position)

        return responseMapper.toPastItemResponse(item, loadProblem(item.problemId))
    }

    /**
     * 오늘 세션을 찾거나, 없으면 만든다. (user_id, session_date) 유니크 제약의 경합을 여기서 멱등하게 흡수한다.
     * 생성은 [SessionCreator]가 REQUIRES_NEW로 격리하므로, 저장이 유니크 제약으로 실패해도 이 트랜잭션은 오염되지 않는다.
     * 동시에 두 요청이 함께 만들려 하면 하나만 성공하고, 진 쪽은 DataIntegrityViolationException을 받아 이미 만들어진 세션을 재조회한다.
     * 재조회가 비면(우리가 아는 경합이 아니면) DIV를 그대로 흘려보낸다 — 전역 핸들러가 중립 CONFLICT(409)로 매핑하므로,
     * 계정 슬라이스의 EMAIL_DUPLICATED로 오분류되지 않는다.
     */
    private fun findOrCreateToday(userId: Long): Session {
        val today = today()
        sessionRepository.findByUserIdAndSessionDate(userId, today)?.let { return it }

        return try {
            sessionCreator.createInNewTransaction(userId, today)
        } catch (e: DataIntegrityViolationException) {
            sessionRepository.findByUserIdAndSessionDate(userId, today) ?: throw e
        }
    }

    /** 오늘 세션을 로드하되, 아직 시작하지 않았으면 404다(제출·공개·지난 문제는 세션을 만들지 않는다). */
    private fun loadTodayOrThrow(userId: Long): Session =
        sessionRepository.findByUserIdAndSessionDate(userId, today())
            ?: throw SessionNotFoundException.forToday()

    private fun loadProblem(problemId: Long): Problem =
        problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

    private fun loadUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }

    private fun today(): LocalDate = LocalDate.now(clock)
}
