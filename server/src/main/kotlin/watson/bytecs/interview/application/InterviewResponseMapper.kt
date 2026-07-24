package watson.bytecs.interview.application

import org.springframework.stereotype.Component
import watson.bytecs.account.domain.StudyStreak
import watson.bytecs.interview.application.dto.InterviewAnswerResponse
import watson.bytecs.interview.application.dto.InterviewHintRevealResponse
import watson.bytecs.interview.application.dto.InterviewSessionResponse
import watson.bytecs.interview.application.dto.RubricPointResultResponse
import watson.bytecs.interview.domain.InterviewPrompt
import watson.bytecs.interview.domain.InterviewSession
import watson.bytecs.interview.domain.JudgeResult
import watson.bytecs.session.application.dto.StreakResponse

/** 면접 세션 도메인·콘텐츠를 응답 DTO로 변환한다. 모범 설명·루브릭은 제출 후에만 노출하는 규칙을 여기 응집한다(no-leak). */
@Component
class InterviewResponseMapper {

    /**
     * currentHintCount·currentRevealedHints는 현재 질문에서 이미 공개한 힌트 수([revealedHintCount])만큼만 잘라 싣는다
     * (no-leak·재진입 복원, SessionResponseMapper.toStateResponse 관례 미러). 현재 질문이 없으면(완료) 둘 다 null이다.
     */
    fun toSessionResponse(
        session: InterviewSession,
        currentPrompt: InterviewPrompt?,
        revealedHintCount: Int = session.currentRevealedHintCount(),
    ): InterviewSessionResponse =
        InterviewSessionResponse(
            sessionId = session.id,
            sessionDate = session.sessionDate,
            status = session.status.name,
            position = session.currentPosition,
            totalCount = session.totalCount,
            currentQuestion = currentPrompt?.question,
            currentConceptName = currentPrompt?.concept?.name,
            currentPromptId = currentPrompt?.id,
            currentHintCount = currentPrompt?.hintCount,
            currentRevealedHints = currentPrompt?.revealedHints(revealedHintCount),
        )

    /**
     * 답 제출 결과를 변환한다. [judgeResult]가 null이면 폴백(채점 실패)이고, points는 빈 목록·comment는 안내 문구다.
     * modelAnswer는 채점 성공·폴백 관계없이 제출한 그 질문([submittedPrompt])에서 항상 채운다(계획 §3.3 폴백 원칙).
     */
    fun toAnswerResponse(
        session: InterviewSession,
        submittedPrompt: InterviewPrompt,
        judgeResult: JudgeResult?,
        nextPrompt: InterviewPrompt?,
        streak: StudyStreak?,
        reviewProblemId: Long?,
    ): InterviewAnswerResponse {
        val points = judgeResult?.satisfiedPoints?.mapIndexed { index, satisfied ->
            RubricPointResultResponse(text = submittedPrompt.rubricPoints[index], satisfied = satisfied)
        } ?: emptyList()

        return InterviewAnswerResponse(
            judged = judgeResult != null,
            points = points,
            comment = judgeResult?.comment ?: FALLBACK_COMMENT,
            modelAnswer = submittedPrompt.modelAnswer,
            conceptName = submittedPrompt.concept.name,
            status = session.status.name,
            position = session.currentPosition,
            totalCount = session.totalCount,
            nextQuestion = nextPrompt?.question,
            nextConceptName = nextPrompt?.concept?.name,
            nextPromptId = nextPrompt?.id,
            // 새 질문은 공개 0에서 시작하므로 개수만 싣는다(공개 목록은 불필요).
            nextHintCount = nextPrompt?.hintCount,
            practicedConceptCount = if (session.isCompleted) session.totalCount else null,
            streak = streak?.let { StreakResponse(count = it.count, lastStudyDate = it.lastStudyDate) },
            reviewProblemId = reviewProblemId,
        )
    }

    /** 면접 힌트 열기 결과를 변환한다. 전체 힌트 수와, 공개분만 담은 전체 목록을 돌려준다(no-leak, HintRevealResponse 관례 미러). */
    fun toHintRevealResponse(prompt: InterviewPrompt, revealedHintCount: Int): InterviewHintRevealResponse =
        InterviewHintRevealResponse(
            hintCount = prompt.hintCount,
            revealedHints = prompt.revealedHints(revealedHintCount),
        )

    companion object {
        // 계획 §4.3 클라이언트 문구를 그대로 따른다.
        private const val FALLBACK_COMMENT = "채점을 잠시 쉬어갈게요 — 모범 설명과 비교해보세요."
    }
}
