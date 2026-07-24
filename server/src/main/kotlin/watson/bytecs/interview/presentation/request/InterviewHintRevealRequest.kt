package watson.bytecs.interview.presentation.request

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

/**
 * 면접 힌트 열기 요청(session.presentation.request.HintRevealRequest 관례 미러). revealedCount는 클라이언트가 아는
 * 현재 공개 수로, 서버는 이 값이 실제 공개 수와 일치할 때만 하나 더 연다(더블탭·경쟁 안전).
 */
data class InterviewHintRevealRequest(
    @field:NotNull(message = "현재 공개 수는 필수입니다.")
    @field:PositiveOrZero(message = "현재 공개 수는 0 이상이어야 합니다.")
    val revealedCount: Int?,
)
