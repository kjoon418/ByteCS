package watson.bytecs.account.presentation

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.application.AccountService
import watson.bytecs.account.application.dto.GuestResponse

@RestController
@RequestMapping("/api/guests")
class GuestController(
    private val accountService: AccountService,
) {

    /** 가입 없이 학습을 시작할 수 있도록 게스트 계정과 토큰을 발급한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createGuest(): GuestResponse =
        accountService.createGuest()
}
