package watson.bytecs.interview.data

import kotlinx.serialization.Serializable
import watson.bytecs.interview.ExplanationJudgeResult
import watson.bytecs.interview.ExplanationOutcome
import watson.bytecs.interview.InterviewCompletion
import watson.bytecs.interview.InterviewItem
import watson.bytecs.interview.InterviewReadiness
import watson.bytecs.interview.InterviewSession
import watson.bytecs.interview.InterviewSessionStatus
import watson.bytecs.interview.InterviewStatus
import watson.bytecs.interview.RubricPoint
import watson.bytecs.session.Streak

/**
 * 백엔드 `/api/interview` 계약과 1:1 대응하는 유선(wire) DTO. 도메인 모델과 분리해, API 형태가 바뀌어도
 * 매핑 한곳만 고치면 되게 한다(session·categoryhistory 슬라이스와 같은 관례).
 *
 * [review-todo] `reviewProblemId`(DI10 — '그때 푼 문제 다시 보기')는 서버가 아직 내려주지 않는다.
 * 매핑은 항상 null로 채운다 — [watson.bytecs.interview.ReviewNudge]가 이미 null을 무낙인으로 처리한다
 * (링크 없이 "복습에서도 곧 다시 만나요" 문구만). 서버가 지원을 추가하면 이 파일만 고치면 된다.
 */

@Serializable
internal data class InterviewStatusDto(
    val candidateConceptCount: Int,
    val remainingQuota: Int,
    val isGuest: Boolean,
) {
    fun toDomain(): InterviewStatus = InterviewStatus(
        guest = isGuest,
        candidateCount = candidateConceptCount,
        remainingToday = remainingQuota,
    )
}

/** `POST /api/interview/sessions/today`·`GET /api/interview/sessions/today` 공통 응답. */
@Serializable
internal data class InterviewSessionDto(
    val sessionId: Long,
    val sessionDate: String,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val currentQuestion: String? = null,
    val currentConceptName: String? = null,
    val currentPromptId: Long? = null,
) {
    fun toDomain(): InterviewSession = InterviewSession(
        sessionId = sessionId,
        sessionDate = sessionDate,
        status = InterviewSessionStatus.from(status),
        position = position,
        totalCount = totalCount,
        currentItem = currentQuestion?.let {
            InterviewItem(
                position = position,
                conceptName = requireNotNull(currentConceptName) { "currentQuestion이 있으면 currentConceptName도 있어야 합니다." },
                question = it,
                promptId = requireNotNull(currentPromptId) { "currentQuestion이 있으면 currentPromptId도 있어야 합니다." },
            )
        },
    )
}

@Serializable
internal data class RubricPointResultDto(
    val text: String,
    val satisfied: Boolean,
) {
    fun toDomain(): RubricPoint = RubricPoint(text, satisfied)
}

@Serializable
internal data class InterviewStreakDto(
    val count: Int,
    val lastStudyDate: String? = null,
) {
    fun toDomain(): Streak = Streak(count, lastStudyDate)
}

/** `POST /api/interview/sessions/today/answers` 요청 본문. */
@Serializable
internal data class InterviewAnswerRequestDto(
    val explanation: String,
)

/** `POST /api/interview/sessions/today/answers` 응답. */
@Serializable
internal data class InterviewAnswerResponseDto(
    val judged: Boolean,
    val points: List<RubricPointResultDto> = emptyList(),
    val comment: String,
    val modelAnswer: String,
    val conceptName: String,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val nextQuestion: String? = null,
    val nextConceptName: String? = null,
    val nextPromptId: Long? = null,
    val practicedConceptCount: Int? = null,
    val streak: InterviewStreakDto? = null,
) {
    fun toDomain(): ExplanationOutcome {
        val domainPoints = points.map { it.toDomain() }
        val readiness = when {
            !judged -> InterviewReadiness.UNVERIFIED
            domainPoints.isEmpty() -> InterviewReadiness.UNVERIFIED
            domainPoints.all { it.satisfied } -> InterviewReadiness.VERIFIED
            domainPoints.any { it.satisfied } -> InterviewReadiness.PARTIAL
            else -> InterviewReadiness.UNVERIFIED
        }
        return ExplanationOutcome(
            result = ExplanationJudgeResult(
                points = domainPoints,
                comment = comment,
                fallback = !judged,
                readiness = readiness,
            ),
            modelAnswer = modelAnswer,
            conceptName = conceptName,
            // [review-todo] DI10 미제공 — 항상 null(위 파일 KDoc 참고).
            reviewProblemId = null,
            status = InterviewSessionStatus.from(status),
            nextItem = nextQuestion?.let {
                InterviewItem(
                    position = position,
                    conceptName = requireNotNull(nextConceptName) { "nextQuestion이 있으면 nextConceptName도 있어야 합니다." },
                    question = it,
                    promptId = requireNotNull(nextPromptId) { "nextQuestion이 있으면 nextPromptId도 있어야 합니다." },
                )
            },
            completion = practicedConceptCount?.let {
                InterviewCompletion(practicedConceptCount = it, streak = streak?.toDomain())
            },
        )
    }
}
