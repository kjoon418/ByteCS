package watson.bytecs.account.presentation

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.application.AccountService
import watson.bytecs.account.application.dto.UserResponse
import watson.bytecs.account.presentation.request.UpdateSettingsRequest
import watson.bytecs.account.security.AuthenticatedUser

/**
 * 인증된 사용자 본인(me)의 조회·설정·탈퇴를 담당한다.
 * 대상 사용자는 토큰에서 복원한 principal로만 결정해, 다른 사용자를 조작할 수 없게 한다.
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val accountService: AccountService,
) {

    @GetMapping("/me")
    fun getMe(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): UserResponse =
        accountService.getMe(user.userId)

    @PatchMapping("/me/settings")
    fun updateSettings(
        @Valid @RequestBody request: UpdateSettingsRequest,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): UserResponse =
        accountService.updateSettings(user.userId, request.dailySessionSize!!)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMe(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        accountService.deleteMe(user.userId)
    }
}
