package watson.bytecs.session.application

import org.springframework.stereotype.Component
import watson.bytecs.account.domain.StudyStreak
import watson.bytecs.problem.domain.AttemptOutcome
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem
import watson.bytecs.session.application.dto.HintRevealResponse
import watson.bytecs.session.application.dto.PastItemResponse
import watson.bytecs.session.application.dto.RevealResponse
import watson.bytecs.session.application.dto.RevealedHintResponse
import watson.bytecs.session.application.dto.SessionAttemptResponse
import watson.bytecs.session.application.dto.SessionProblemResponse
import watson.bytecs.session.application.dto.SessionStateResponse
import watson.bytecs.session.application.dto.StreakResponse
import watson.bytecs.session.domain.Session
import watson.bytecs.session.domain.SessionItem

/**
 * 세션 도메인·콘텐츠를 응답 DTO로 변환한다.
 * 개념·정답은 정답 처리 후(정답 제출·정답 공개·지난 문제)에만 실리도록 노출 규칙을 이 한곳에 응집한다(no-leak).
 */
@Component
class SessionResponseMapper {

    /**
     * 지금 풀 문제를 무낙인 형태로 변환한다(개념·허용답·해설 제외).
     * 힌트는 개수만 항상 싣고, 본문은 이 칸에서 이미 공개한 수([revealedHintCount])만큼만 잘라 싣는다(no-leak·재진입 복원).
     */
    fun toProblemResponse(problem: Problem, revealedHintCount: Int): SessionProblemResponse =
        SessionProblemResponse(
            id = problem.id,
            question = problem.questionText,
            difficulty = problem.difficulty?.name,
            codeSnippet = problem.codeSnippet,
            hintCount = problem.hintCount,
            revealedHints = toRevealedHints(problem, revealedHintCount),
        )

    fun toStateResponse(session: Session, currentProblem: Problem?, streak: StudyStreak): SessionStateResponse =
        SessionStateResponse(
            sessionId = session.id,
            sessionDate = session.sessionDate,
            status = session.status.name,
            solvedCount = session.solvedCount,
            totalCount = session.totalCount,
            position = session.currentPosition,
            currentProblem = currentProblem?.let { toProblemResponse(it, session.currentRevealedHintCount()) },
            streak = toStreakResponse(streak),
        )

    /**
     * 답 제출 결과를 변환한다.
     * 개념·해설은 정답일 때만 방금 통과한 문제([attemptedProblem])에서 채우고, currentProblem은 전진 후의 무낙인 문제다.
     * 오답 교정 힌트는 [outcome]에 실려 온 것을 그대로 싣는다(비정답·예상 오답 매칭 시에만 non-null).
     * streak는 이 제출로 세션이 완료됐을 때만 전달된다.
     */
    fun toAttemptResponse(
        session: Session,
        outcome: AttemptOutcome,
        attemptedProblem: Problem,
        nextProblem: Problem?,
        streak: StudyStreak?,
    ): SessionAttemptResponse {
        val correct = outcome.judgement == Judgement.CORRECT

        return SessionAttemptResponse(
            result = outcome.judgement.name,
            status = session.status.name,
            solvedCount = session.solvedCount,
            totalCount = session.totalCount,
            position = session.currentPosition,
            concept = if (correct) attemptedProblem.concept.name else null,
            explanation = if (correct) attemptedProblem.explanation else null,
            misconceptionHint = outcome.misconceptionHint,
            // 전진 후의 현재 칸이므로, 그 칸의 공개 힌트 수로 복원한다(새 문제라면 0).
            currentProblem = nextProblem?.let { toProblemResponse(it, session.currentRevealedHintCount()) },
            streak = streak?.let { toStreakResponse(it) },
        )
    }

    /** 힌트 열기 결과를 변환한다. 전체 힌트 수와, 공개분만 담은 전체 목록을 돌려준다(no-leak). */
    fun toHintRevealResponse(problem: Problem, revealedHintCount: Int): HintRevealResponse =
        HintRevealResponse(
            hintCount = problem.hintCount,
            revealedHints = toRevealedHints(problem, revealedHintCount),
        )

    /** 스트릭 도메인 값을 응답 DTO로 변환한다(오늘 상태·완료 신호가 같은 규칙을 공유하도록 한곳에 응집). */
    private fun toStreakResponse(streak: StudyStreak): StreakResponse =
        StreakResponse(count = streak.count, lastStudyDate = streak.lastStudyDate)

    fun toRevealResponse(problem: Problem): RevealResponse =
        RevealResponse(
            concept = problem.concept.name,
            explanation = problem.explanation,
            acceptableAnswers = acceptableAnswers(problem),
        )

    fun toPastItemResponse(item: SessionItem, problem: Problem): PastItemResponse =
        PastItemResponse(
            position = item.position,
            problemId = problem.id,
            question = problem.questionText,
            codeSnippet = problem.codeSnippet,
            difficulty = problem.difficulty?.name,
            submittedAnswer = item.submittedAnswer,
            // 지난 문제는 정답으로 통과한 칸만 열리므로 판정은 항상 CORRECT다.
            result = Judgement.CORRECT.name,
            revealed = item.revealed,
            concept = problem.concept.name,
            explanation = problem.explanation,
            acceptableAnswers = acceptableAnswers(problem),
        )

    /**
     * 모범답안 목록을 결정적 순서로 돌려준다.
     * 허용답 집합은 순서가 없으므로, 가장 짧은 표기를 앞에 두고 동률이면 사전순으로 고정해 같은 문제엔 항상 같은 순서를 준다.
     */
    private fun acceptableAnswers(problem: Problem): List<String> =
        problem.acceptableAnswers.sortedWith(compareBy({ it.length }, { it }))

    /** 이미 공개한 힌트만 약→강 순으로 응답 형태로 바꾼다(도메인이 [revealedHintCount]로 절단해 no-leak을 보장한다). */
    private fun toRevealedHints(problem: Problem, revealedHintCount: Int): List<RevealedHintResponse> =
        problem.revealedHints(revealedHintCount).map(::toRevealedHint)

    private fun toRevealedHint(hint: Hint): RevealedHintResponse =
        RevealedHintResponse(text = hint.text, codeSnippet = hint.codeSnippet)
}
