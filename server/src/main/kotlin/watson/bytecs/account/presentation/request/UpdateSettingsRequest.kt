package watson.bytecs.account.presentation.request

import jakarta.validation.constraints.NotNull

/**
 * 학습 설정 변경 요청. 값 누락만 검증하고, 허용 범위는 도메인(UserSettings)이 강제한다.
 */
data class UpdateSettingsRequest(
    @field:NotNull(message = "일일 세션 분량은 필수입니다.")
    val dailySessionSize: Int?,
)
