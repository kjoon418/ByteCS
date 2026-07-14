package watson.bytecs.session.application.dto

import java.time.LocalDate

/**
 * 오늘의 세션 상태(GET /today) 응답. 중단 후 재개 시 이 상태로 이어서 푼다.
 * position은 지금 풀어야 할 칸의 위치(=완료한 본 문제 수), currentProblem은 그 칸의 무낙인 문제(완료 시 null)다.
 * streak은 홈 화면(디자인 02)이 로드 시점에 바로 보여줄 현재 연속 학습 상태다(사용자 속성, 기본 count=0이라 항상 존재).
 */
data class SessionStateResponse(
    val sessionId: Long,
    val sessionDate: LocalDate,
    val status: String,
    val solvedCount: Int,
    val totalCount: Int,
    val position: Int,
    val currentProblem: SessionProblemResponse?,
    val streak: StreakResponse,
)
