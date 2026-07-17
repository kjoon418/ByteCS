package watson.bytecs.study.presentation

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import watson.bytecs.account.security.AuthenticatedUser
import watson.bytecs.study.application.CategoryHistoryService
import watson.bytecs.study.application.dto.CategoryHistoryResponse

/**
 * 인증된 사용자 본인이 푼 문제를 카테고리별로 조회한다(명세 §7 '카테고리별 학습 이력', 읽기 전용).
 * 자원은 토큰에서 복원한 principal(userId)로만 결정해, 다른 사용자의 학습 이력을 열람할 수 없게 한다.
 * `/api/learning-history` 하위 경로는 SecurityConfig의 permitAll 목록에 없으므로 기본 규칙에 따라 인증이 강제된다.
 */
@RestController
@RequestMapping("/api/learning-history")
class CategoryHistoryController(
    private val categoryHistoryService: CategoryHistoryService,
) {

    @GetMapping("/categories")
    fun getByCategory(
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): List<CategoryHistoryResponse> =
        categoryHistoryService.findByCategory(user.userId)
}
