package watson.bytecs.account.application

import org.springframework.stereotype.Component
import watson.bytecs.account.application.dto.GuestResponse
import watson.bytecs.account.application.dto.UserResponse
import watson.bytecs.account.domain.User

/**
 * 사용자 엔티티를 응답 DTO로 변환한다.
 * 비밀번호 해시 같은 민감 필드가 응답에 새어 나가지 않도록 노출 필드를 이 한곳에 응집한다.
 */
@Component
class AccountResponseMapper {

    fun toUserResponse(user: User): UserResponse =
        UserResponse(
            userId = user.id,
            role = user.role.name,
            email = user.email,
            dailySessionSize = user.settings.dailySessionSize,
            preferredDifficulty = user.settings.preferredDifficulty?.name,
        )

    fun toGuestResponse(token: String, user: User): GuestResponse =
        GuestResponse(
            token = token,
            userId = user.id,
            role = user.role.name,
        )
}
