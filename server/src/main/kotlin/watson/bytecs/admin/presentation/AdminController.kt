package watson.bytecs.admin.presentation

import java.security.Principal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import watson.bytecs.admin.application.AdminStatsService

/**
 * 관리자 페이지 진입점. Phase 1에서는 로그인·홈 골격만 두고,
 * 문제 목록·검수·반입 화면은 파이프라인 Phase 2~3에서 채운다(구현 계획 §5.2 A1~A9).
 * 테스터 기간 지표(풀이 시작·완료·완료 후 추가 학습)는 /admin/stats에서 확인한다.
 */
@Controller
@RequestMapping("/admin")
class AdminController(
    private val adminStatsService: AdminStatsService,
) {

    @GetMapping
    fun home(principal: Principal, model: Model): String {
        model.addAttribute("adminEmail", principal.name)
        return "admin/home"
    }

    @GetMapping("/login")
    fun loginPage(): String = "admin/login"

    @GetMapping("/stats")
    fun stats(principal: Principal, model: Model): String {
        model.addAttribute("adminEmail", principal.name)
        model.addAttribute("metrics", adminStatsService.collectTesterMetrics())
        return "admin/stats"
    }
}
