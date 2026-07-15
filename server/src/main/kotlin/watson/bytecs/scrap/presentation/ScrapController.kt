package watson.bytecs.scrap.presentation

import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.scrap.application.ScrapService

/**
 * 인증된 사용자 본인의 스크랩 토글(추가·해제). 둘 다 멱등하다.
 * 대상 사용자는 토큰에서 복원한 principal(userId)로만 결정한다. 이 경로는 SecurityConfig에서 인증을 명시적으로 요구한다
 * (문제 조회 permitAll보다 앞선 규칙).
 */
@RestController
@RequestMapping("/api/problems/{problemId}/scraps")
class ScrapController(
    private val scrapService: ScrapService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun scrap(
        @PathVariable problemId: Long,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        scrapService.scrap(user.userId, problemId)
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unscrap(
        @PathVariable problemId: Long,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        scrapService.unscrap(user.userId, problemId)
    }
}
