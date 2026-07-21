package watson.bytecs.session.application.dto

import java.time.LocalDate

/**
 * 오늘의 세션 상태(GET /today) 응답. 중단 후 재개 시 이 상태로 이어서 푼다.
 * position은 지금 풀어야 할 칸의 위치(=완료한 본 문제 수), currentProblem은 그 칸의 무낙인 문제(완료 시 null)다.
 * streak은 홈 화면(디자인 02)이 로드 시점에 바로 보여줄 현재 연속 학습 상태다(사용자 속성, 기본 count=0이라 항상 존재).
 * needsDifficultyPrompt는 완료 화면에서 난이도 제안을 노출할지 여부다 — 서버가 단일 출처로 판단한다(선호 미설정 && 미응답).
 * 완료 상태(재진입 포함)에서 클라이언트가 이 값으로 제안 카드를 노출한다(진행 중에도 실려 오지만 완료 화면에서만 사용).
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
    val needsDifficultyPrompt: Boolean,
)
