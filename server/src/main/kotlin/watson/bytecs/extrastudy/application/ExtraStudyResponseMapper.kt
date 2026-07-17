package watson.bytecs.extrastudy.application

import org.springframework.stereotype.Component
import watson.bytecs.extrastudy.application.dto.ExtraStudyAttemptResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyCurrentResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyHintRevealResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyProblemResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyRevealResponse
import watson.bytecs.extrastudy.application.dto.ExtraStudyRevealedHintResponse
import watson.bytecs.problem.application.dto.EnrichmentResponse
import watson.bytecs.problem.domain.AttemptOutcome
import watson.bytecs.problem.domain.Hint
import watson.bytecs.problem.domain.Judgement
import watson.bytecs.problem.domain.Problem

/**
 * 추가 학습 도메인·콘텐츠를 응답 DTO로 변환한다.
 * 개념·정답은 정답 처리 후(정답 제출·정답 공개)에만 실리도록 노출 규칙을 이 한곳에 응집한다(no-leak).
 */
@Component
class ExtraStudyResponseMapper {

    /**
     * 지금 풀 문제를 무낙인 형태로 변환한다(개념·허용답·해설 제외).
     * 힌트는 개수만 항상 싣고, 본문은 이 열린 항목에서 이미 공개한 수([revealedHintCount])만큼만 잘라 싣는다(no-leak·재진입 복원).
     */
    fun toProblemResponse(problem: Problem, revealedHintCount: Int): ExtraStudyProblemResponse =
        ExtraStudyProblemResponse(
            id = problem.id,
            question = problem.questionText,
            difficulty = problem.difficulty?.name,
            codeSnippet = problem.codeSnippet,
            hintCount = problem.hintCount,
            revealedHints = toRevealedHints(problem, revealedHintCount),
            category = problem.representativeCategory()?.name,
        )

    /** 풀 문제가 있을 때의 현재 상태(exhausted=false). */
    fun toCurrentResponse(problem: Problem, revealedHintCount: Int): ExtraStudyCurrentResponse =
        ExtraStudyCurrentResponse(exhausted = false, problem = toProblemResponse(problem, revealedHintCount))

    /** 소진 상태(모두 풀었고 도래 복습 없음). 오류가 아니라 정상 상태다. */
    fun exhausted(): ExtraStudyCurrentResponse =
        ExtraStudyCurrentResponse(exhausted = true, problem = null)

    /**
     * 답 제출 결과를 변환한다. 개념·해설·심화·대표 정답은 정답일 때만 방금 통과한 [problem]에서 채운다(no-leak).
     * 오답 교정 힌트는 [outcome]에 실려 온 것을 그대로 싣는다(비정답·예상 오답 매칭 시에만 non-null).
     */
    fun toAttemptResponse(outcome: AttemptOutcome, problem: Problem): ExtraStudyAttemptResponse {
        val correct = outcome.judgement == Judgement.CORRECT
        return ExtraStudyAttemptResponse(
            result = outcome.judgement.name,
            concepts = if (correct) problem.conceptNames() else null,
            explanation = if (correct) problem.explanation else null,
            enrichment = if (correct) problem.enrichment?.let(EnrichmentResponse::from) else null,
            representativeAnswer = if (correct) problem.representativeAnswer else null,
            misconceptionHint = outcome.misconceptionHint,
        )
    }

    fun toRevealResponse(problem: Problem): ExtraStudyRevealResponse =
        ExtraStudyRevealResponse(
            concepts = problem.conceptNames(),
            explanation = problem.explanation,
            enrichment = problem.enrichment?.let(EnrichmentResponse::from),
            representativeAnswer = problem.representativeAnswer,
            category = problem.representativeCategory()?.name,
        )

    /** 힌트 열기 결과를 변환한다. 전체 힌트 수와, 공개분만 담은 전체 목록을 돌려준다(no-leak). */
    fun toHintRevealResponse(problem: Problem, revealedHintCount: Int): ExtraStudyHintRevealResponse =
        ExtraStudyHintRevealResponse(
            hintCount = problem.hintCount,
            revealedHints = toRevealedHints(problem, revealedHintCount),
        )

    /** 이미 공개한 힌트만 약→강 순으로 응답 형태로 바꾼다(도메인이 [revealedHintCount]로 절단해 no-leak을 보장한다). */
    private fun toRevealedHints(problem: Problem, revealedHintCount: Int): List<ExtraStudyRevealedHintResponse> =
        problem.revealedHints(revealedHintCount).map(::toRevealedHint)

    private fun toRevealedHint(hint: Hint): ExtraStudyRevealedHintResponse =
        ExtraStudyRevealedHintResponse(text = hint.text, codeSnippet = hint.codeSnippet)
}
