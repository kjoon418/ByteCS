package watson.bytecs.interview

import watson.bytecs.session.Streak

/**
 * 결정적 인메모리 [InterviewRepository]. VM 상태 전이를 네트워크 없이 검증한다.
 * 채점 결과는 [onSubmit]로 스크립팅하고, 오류는 필드로 주입한다(FakeSessionRepository와 같은 관례).
 */
class FakeInterviewRepository(
    var statusResult: InterviewStatus = InterviewStatus(guest = false, candidateCount = 3, remainingToday = 1),
    var session: InterviewSession = activeSession(),
) : InterviewRepository {

    var statusError: Throwable? = null
    var startError: Throwable? = null
    var submitError: Throwable? = null

    var statusCount = 0
    var startCount = 0
    var submitCount = 0
    val submitted = mutableListOf<Pair<Int, String>>()

    /** 제출 결과 공급자. 인자는 (position, text). 기본은 단일 문항 완료(성공·검증됨). */
    var onSubmit: (Int, String) -> ExplanationOutcome = { _, _ ->
        outcome(
            result = successResult(InterviewReadiness.VERIFIED, satisfied = 2, unsatisfied = 0),
            next = null,
            completion = InterviewCompletion(practicedConceptCount = 1, streak = null),
        )
    }

    override suspend fun status(): InterviewStatus {
        statusCount++
        statusError?.let { throw it }
        return statusResult
    }

    override suspend fun startOrResumeToday(): InterviewSession {
        startCount++
        startError?.let { throw it }
        return session
    }

    override suspend fun submitExplanation(position: Int, text: String): ExplanationOutcome {
        submitCount++
        submitted += position to text
        submitError?.let { throw it }
        return onSubmit(position, text)
    }

    companion object {
        fun item(position: Int = 0, promptId: Long = 100L + position) = InterviewItem(
            position = position,
            conceptName = "개념$position",
            question = "질문$position 을 설명해보세요",
            promptId = promptId,
        )

        fun activeSession(
            position: Int = 0,
            total: Int = 3,
            item: InterviewItem? = item(position),
        ) = InterviewSession(
            sessionId = 1L,
            sessionDate = "2026-07-22",
            status = InterviewSessionStatus.IN_PROGRESS,
            position = position,
            totalCount = total,
            currentItem = item,
        )

        fun completedSession(total: Int = 3) = InterviewSession(
            sessionId = 1L,
            sessionDate = "2026-07-22",
            status = InterviewSessionStatus.COMPLETED,
            position = total,
            totalCount = total,
            currentItem = null,
        )

        /** 충족 [satisfied]개 + 미충족 [unsatisfied]개로 이뤄진 루브릭 포인트 목록. */
        fun points(satisfied: Int, unsatisfied: Int): List<RubricPoint> =
            List(satisfied) { RubricPoint("짚은 포인트$it", satisfied = true) } +
                List(unsatisfied) { RubricPoint("보완 포인트$it", satisfied = false) }

        fun successResult(
            readiness: InterviewReadiness,
            satisfied: Int,
            unsatisfied: Int,
            comment: String? = null,
        ) = ExplanationJudgeResult(
            points = points(satisfied, unsatisfied),
            comment = comment,
            fallback = false,
            readiness = readiness,
        )

        fun fallbackResult() = ExplanationJudgeResult(
            points = emptyList(),
            comment = null,
            fallback = true,
            readiness = InterviewReadiness.UNVERIFIED,
        )

        fun outcome(
            result: ExplanationJudgeResult,
            next: InterviewItem?,
            completion: InterviewCompletion? = null,
            modelAnswer: String = "모범 설명입니다",
            conceptName: String = "개념",
            reviewProblemId: Long? = null,
        ) = ExplanationOutcome(
            result = result,
            modelAnswer = modelAnswer,
            conceptName = conceptName,
            reviewProblemId = reviewProblemId,
            status = if (next == null) InterviewSessionStatus.COMPLETED else InterviewSessionStatus.IN_PROGRESS,
            nextItem = next,
            completion = completion,
        )
    }
}
