package watson.bytecs.account.presentation

import org.springframework.stereotype.Component
import watson.bytecs.account.domain.Email
import watson.bytecs.account.domain.RawPassword
import watson.bytecs.account.presentation.request.LoginRequest
import watson.bytecs.account.presentation.request.RegisterRequest

/**
 * 원시 값 요청을 도메인 VO로 변환한다.
 * 서비스가 원시 값 대신 VO만 다루도록 컨트롤러 계층에서 변환을 책임진다.
 */
@Component
class AccountRequestMapper {

    // 필수 여부는 Bean Validation(@NotBlank)이 이미 보장하고, 형식 검증은 VO 생성 시점에 수행된다.
    fun toEmail(request: RegisterRequest): Email = Email(request.email!!)

    fun toRawPassword(request: RegisterRequest): RawPassword = RawPassword(request.password!!)

    fun toEmail(request: LoginRequest): Email = Email(request.email!!)

    fun toRawPassword(request: LoginRequest): RawPassword = RawPassword(request.password!!)
}
