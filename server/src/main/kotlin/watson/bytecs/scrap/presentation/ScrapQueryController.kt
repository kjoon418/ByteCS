package watson.bytecs.scrap.presentation

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.scrap.application.ScrapService
import watson.bytecs.scrap.application.dto.ScrapDetailResponse
import watson.bytecs.scrap.application.dto.ScrapSummaryResponse

/**
 * 인증된 사용자 본인의 스크랩 목록·상세를 조회한다.
 * 모든 조회는 principal(userId)로만 결정해, 타인의 스크랩을 열람할 수 없게 한다(사용자 격리).
 * `/api/scraps`는 SecurityConfig의 permitAll 목록에 없으므로 기본 규칙에 따라 인증이 강제된다.
 */
@RestController
@RequestMapping("/api/scraps")
class ScrapQueryController(
    private val scrapService: ScrapService,
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): List<ScrapSummaryResponse> =
        scrapService.list(user.userId)

    @GetMapping("/{problemId}")
    fun detail(
        @PathVariable problemId: Long,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): ScrapDetailResponse =
        scrapService.detail(user.userId, problemId)
}
