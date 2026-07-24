package watson.bytecs.interview.data

import kotlinx.serialization.Serializable
import watson.bytecs.interview.ExplanationJudgeResult
import watson.bytecs.interview.ExplanationOutcome
import watson.bytecs.interview.InterviewCompletion
import watson.bytecs.interview.InterviewHintReveal
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
 */

/**
 * 서버 공통 오류 본문(`{message, errorCode}`)에서 [errorCode]만 읽는다. 면접 예외를 상태 코드가 아니라
 * errorCode로 구별해 '홈으로 되돌릴 상태'와 '전송 실패'를 가른다([KtorInterviewRepository], session 슬라이스 관례).
 */
@Serializable
internal data class InterviewErrorBodyDto(
    val errorCode: String? = null,
)

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
    // 현재 문항의 힌트 총수·이미 공개한 힌트(재진입 복원). currentQuestion이 있으면 둘 다 있다(no-leak과 독립, 무낙인).
    val currentHintCount: Int? = null,
    val currentRevealedHints: List<String>? = null,
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
                hintCount = requireNotNull(currentHintCount) { "currentQuestion이 있으면 currentHintCount도 있어야 합니다." },
                revealedHints = requireNotNull(currentRevealedHints) { "currentQuestion이 있으면 currentRevealedHints도 있어야 합니다." },
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
    // 다음 문항의 전체 힌트 수. 새 문항은 공개 0에서 시작하므로 공개 목록은 싣지 않는다(개수만으로 진입점 노출 판단).
    val nextHintCount: Int? = null,
    val practicedConceptCount: Int? = null,
    val streak: InterviewStreakDto? = null,
    // '검증됨' 미달일 때만 채워지는 '그때 푼 문제 다시 보기'(DI10) 대상 id. 검증됨·폴백이면 null(서버 생략 시 기본값).
    val reviewProblemId: Long? = null,
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
            // '검증됨' 미달일 때 서버가 실어 준 재열람 대상(DI10) — 없으면 null이라 화면은 링크를 숨긴다.
            reviewProblemId = reviewProblemId,
            status = InterviewSessionStatus.from(status),
            nextItem = nextQuestion?.let {
                InterviewItem(
                    position = position,
                    conceptName = requireNotNull(nextConceptName) { "nextQuestion이 있으면 nextConceptName도 있어야 합니다." },
                    question = it,
                    promptId = requireNotNull(nextPromptId) { "nextQuestion이 있으면 nextPromptId도 있어야 합니다." },
                    hintCount = requireNotNull(nextHintCount) { "nextQuestion이 있으면 nextHintCount도 있어야 합니다." },
                    // 새 문항은 항상 공개 0에서 시작 — 서버도 목록을 싣지 않는다.
                    revealedHints = emptyList(),
                )
            },
            completion = practicedConceptCount?.let {
                InterviewCompletion(practicedConceptCount = it, streak = streak?.toDomain())
            },
        )
    }
}

/**
 * `POST /api/interview/sessions/today/hints/reveal` 요청 본문. 클라가 아는 현재 공개 수를 싣는다 —
 * 서버는 현재 [revealedCount]와 일치할 때만 +1 한다(더블탭·경쟁 안전, 03 HintRevealRequestDto와 같은 관례).
 */
@Serializable
internal data class InterviewHintRevealRequestDto(
    val revealedCount: Int,
)

/** `POST /api/interview/sessions/today/hints/reveal` 응답(공개 후 전체 목록). 텍스트만 다뤄 codeSnippet이 없다. */
@Serializable
internal data class InterviewHintRevealResponseDto(
    val hintCount: Int,
    val revealedHints: List<String> = emptyList(),
) {
    fun toDomain(): InterviewHintReveal = InterviewHintReveal(hintCount, revealedHints)
}
