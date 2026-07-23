package watson.bytecs.interview.application.dto

import java.time.LocalDate

/**
 * 면접 세션 상태 응답(생성·재개 조회 공통). currentQuestion은 질문 문구만 싣는다 —
 * 모범 설명·루브릭은 no-leak으로 제출 후에만 공개한다([InterviewAnswerResponse]).
 */
data class InterviewSessionResponse(
    val sessionId: Long,
    val sessionDate: LocalDate,
    val status: String,
    val position: Int,
    val totalCount: Int,
    val currentQuestion: String?,
)
