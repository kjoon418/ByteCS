package watson.bytecs.session.application

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
import watson.bytecs.study.LearningHistory
import java.time.Clock
import java.time.LocalDate

/**
 * 오늘의 한입(일일 세션)을 조율한다.
 * 판정은 콘텐츠(Problem.judge)에, 진행·완료·정답 공개 규칙은 세션(Session)에, 스트릭은 사용자(User)에 위임하고,
 * 서비스는 '오늘 세션' get-or-create·배정·조회·응답 변환만 담당한다.
 * 하루에 여러 세션이 있을 수 있고(D6·D9 일원화), '오늘의 세션'은 그 날짜의 가장 최근 세션이다.
 * 세션 생성은 '오늘 조회(getOrCreateToday)'와 '조금 더 풀기(getOrCreateNext)'에서만 일어난다.
 * 제출·공개·지난 문제는 이미 시작한 세션을 전제하며, 없으면 404다.
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
    private val learningHistory: LearningHistory,
    private val clock: Clock,
) {

    /**
     * 오늘의 세션(그 날짜의 최신 세션)을 가져오거나(없으면) 새로 배정해 시작 상태를 돌려준다. 하루 경계·중단 재개의 진입점이다.
     * 진행/완료 무관하게 오늘 최신 세션이 있으면 그대로 돌려준다(홈이 '이어서'·'오늘 완료'를 이 상태로 판단한다).
     * 조회 후 없을 때만 만들므로 쓰기 트랜잭션으로 둔다(생성과 조회가 한 트랜잭션에서 원자적으로 일어나게).
     */
    @Transactional
    fun getOrCreateToday(userId: Long): SessionStateResponse {
        val session = findOrCreateToday(userId)
        return toStateResponse(userId, session)
    }

    /**
     * '조금 더 풀기': 오늘 최신 세션이 완료됐으면 새 세션을 시작해 돌려준다.
     *  - 오늘 세션이 없으면 새로 만든다(= getOrCreateToday와 같은 동작).
     *  - 오늘 최신이 진행 중이면 새로 만들지 않고 그 세션을 그대로 돌려준다(중복 세션 방지 — 클라는 200으로 이어서 푼다).
     * 새 세션을 만들 수 있으므로 쓰기 트랜잭션으로 둔다.
     */
    @Transactional
    fun getOrCreateNext(userId: Long): SessionStateResponse {
        val session = advanceOrCreateToday(userId)
        return toStateResponse(userId, session)
    }

    /** 세션 상태 응답을 만든다. 홈 화면이 로드 시점에 바로 스트릭을 보여주도록 현재 스트릭을 함께 싣는다. */
    private fun toStateResponse(userId: Long, session: Session): SessionStateResponse {
        val currentProblem = session.currentItemProblemId()?.let { loadProblem(it) }
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
        // 완료된 세션엔 현재 본 문제가 없다. 도메인 규칙과 같은 의미의 예외로 일관되게 막는다('조금 더 풀기'로 새 세션을 시작한다).
        val currentProblemId = session.currentItemProblemId()
            ?: throw SessionAlreadyCompletedException.forAttempt()
        val problem = loadProblem(currentProblemId)

        // 판정과 오답 교정 힌트를 함께 산출한다(교정 힌트 매칭 시 MISMATCH 확정 — 근접보다 우선). 진행에는 확정된 판정을 쓴다.
        // 정답으로 통과할 칸(= 진입 시점의 현재 칸)의 위치를 미리 잡아, 통과 직후 그 칸의 도움 신호를 읽는다.
        val solvedPosition = session.currentPosition
        // D1(따라 입력): 이 칸에서 정답을 공개했다면 화면의 정답을 옮겨 적는 맥락이므로, 유형과 무관하게 전사 오타를 근접으로 안내한다.
        val typeAlong = session.items[solvedPosition].revealed
        val outcome = problem.evaluate(answer, typeAlong = typeAlong)
        // D8: '이미 풀었던 문제인가'는 recordAttempt로 이번 정답이 반영되기 전에 스냅샷해야 한다 —
        // recordAttempt 이후 조회하면(플러시 등으로) 방금 통과한 이번 정답이 섞여 '이미 풀었음'으로 오판할 수 있다.
        val alreadySolvedBefore = if (outcome.judgement == Judgement.CORRECT) {
            learningHistory.findSolvedProblemIds(userId)
        } else {
            null
        }
        session.recordAttempt(outcome.judgement, answer, misconceptionShown = outcome.misconceptionHint != null)

        // 정답이면 그 문제의 모든 개념 숙련도를 같은 트랜잭션에서 갱신한다(기능 3). 도움 신호는 방금 통과한 칸에서 파생한다.
        if (outcome.judgement == Judgement.CORRECT) {
            val solvedItem = session.items[solvedPosition]
            val signal = MasterySignal.of(
                revealed = solvedItem.revealed,
                revealedHintCount = solvedItem.revealedHintCount,
                misconceptionHintSeen = solvedItem.misconceptionHintSeen,
            )
            val alreadySolved = problem.id in requireNotNull(alreadySolvedBefore)
            reviewService.recordSolve(userId, problem.conceptIds(), signal, today(), problem.id, alreadySolved)
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
     * 오늘 최신 세션을 찾거나, 없으면 만든다.
     * 유니크 제약을 제거한 뒤(D6·D9)의 동시성은 '본인 한정 경합'이다 — 조회와 생성을 한 쓰기 트랜잭션에 담아,
     * 흔한 순차 재요청(같은 사용자가 두 번 조회)은 최신 세션을 그대로 재사용해 중복을 만들지 않는다.
     */
    private fun findOrCreateToday(userId: Long): Session {
        val today = today()
        return findLatestToday(userId, today) ?: sessionCreator.create(userId, today)
    }

    /**
     * '조금 더 풀기'의 세션 선택: 오늘 최신이 완료면 새 세션을, 진행 중이면 그 세션을, 없으면 새 세션을 돌려준다.
     * 진행 중 세션이 있으면 새로 만들지 않는 것으로 '완료 전 중복 세션'을 막는다(트랜잭션 내 진행 중 세션 검사).
     */
    private fun advanceOrCreateToday(userId: Long): Session {
        val today = today()
        val latest = findLatestToday(userId, today)
        return when {
            latest == null || latest.isCompleted -> sessionCreator.create(userId, today)
            else -> latest
        }
    }

    /** 오늘 날짜의 가장 최근 세션(id 내림차순 첫 행). 없으면 null. */
    private fun findLatestToday(userId: Long, today: LocalDate): Session? =
        sessionRepository.findTopByUserIdAndSessionDateOrderByIdDesc(userId, today)

    /** 오늘 최신 세션을 로드하되, 아직 시작하지 않았으면 404다(제출·공개·지난 문제는 세션을 만들지 않는다). */
    private fun loadTodayOrThrow(userId: Long): Session =
        findLatestToday(userId, today())
            ?: throw SessionNotFoundException.forToday()

    private fun loadProblem(problemId: Long): Problem =
        problemRepository.findById(problemId)
            .orElseThrow { ProblemNotFoundException.byId(problemId) }

    private fun loadUser(userId: Long): User =
        userRepository.findById(userId)
            .orElseThrow { UserNotFoundException.byId(userId) }

    private fun today(): LocalDate = LocalDate.now(clock)
}
