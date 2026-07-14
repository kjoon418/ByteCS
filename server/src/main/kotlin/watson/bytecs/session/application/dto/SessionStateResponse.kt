package watson.bytecs.session.application.dto

import java.time.LocalDate

/**
 * 오늘의 세션 상태(GET /today) 응답. 중단 후 재개 시 이 상태로 이어서 푼다.
 * position은 지금 풀어야 할 칸의 위치(=완료한 본 문제 수), currentProblem은 그 칸의 무낙인 문제(완료 시 null)다.
 */
data class SessionStateResponse(
    val sessionId: Long,
    val sessionDate: LocalDate,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val currentProblem: SessionProblemResponse?,
)
