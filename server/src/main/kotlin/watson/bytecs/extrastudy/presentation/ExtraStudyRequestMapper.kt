package watson.bytecs.extrastudy.presentation

import org.springframework.stereotype.Component
import watson.bytecs.extrastudy.presentation.request.ExtraStudyAttemptRequest
import watson.bytecs.problem.domain.AnswerText

/**
 * 원시 값 요청을 도메인 VO로 변환한다.
 * 서비스가 원시 값 대신 VO만 다루도록 컨트롤러 계층에서 변환을 책임진다.
 */
@Component
class ExtraStudyRequestMapper {

    fun toAnswerText(request: ExtraStudyAttemptRequest): AnswerText =
        // answer 필수 여부는 Bean Validation(@NotBlank)이 이미 보장한다.
        AnswerText(request.answer!!)
}
