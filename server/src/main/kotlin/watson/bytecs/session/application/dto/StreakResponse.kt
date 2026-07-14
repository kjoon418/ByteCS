package watson.bytecs.session.application.dto

import java.time.LocalDate

/**
 * 연속 학습 스트릭 응답. 세션 완료 시 갱신된 값을 함께 실어 성취 동기를 전한다.
 */
data class StreakResponse(
    val count: Int,
    val lastStudyDate: LocalDate?,
)
