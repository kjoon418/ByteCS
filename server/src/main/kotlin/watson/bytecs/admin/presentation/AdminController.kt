package watson.bytecs.admin.presentation

import java.security.Principal
import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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

    /**
     * 테스터 지표. [from]·[to](ISO 날짜, KST)를 **둘 다** 주면 그 기간으로 퍼널 지표를 집계하고, 없으면 전체 기간이다.
     * 하나만 주면 무시하고 전체 기간으로 본다(부분 입력 방어 — 서비스가 둘 다 있을 때만 기간으로 친다).
     */
    @GetMapping("/stats")
    fun stats(
        principal: Principal,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        model: Model,
    ): String {
        model.addAttribute("adminEmail", principal.name)
        model.addAttribute("metrics", adminStatsService.collectTesterMetrics(from, to))
        model.addAttribute("from", from)
        model.addAttribute("to", to)
        model.addAttribute("periodApplied", from != null && to != null)
        return "admin/stats"
    }
}
