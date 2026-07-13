package watson.bytecs.problem.presentation

import org.springframework.stereotype.Component
import watson.bytecs.problem.domain.AnswerText
import watson.bytecs.problem.presentation.request.AttemptRequest

/**
 * 원시 값 요청을 도메인 VO로 변환한다.
 * 서비스가 원시 값 대신 VO만 다루도록 컨트롤러 계층에서 변환을 책임진다.
 */
@Component
class ProblemRequestMapper {

    fun toAnswerText(request: AttemptRequest): AnswerText =
        // answer 필수 여부는 Bean Validation(@NotBlank)이 이미 보장한다.
        AnswerText(request.answer!!)
}
