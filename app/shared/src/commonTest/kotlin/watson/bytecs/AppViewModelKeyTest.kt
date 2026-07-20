package watson.bytecs

import watson.bytecs.report.ReportCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 상세 화면 뷰모델 캐시 키([detailViewModelKey])의 유일성 불변식을 못박는다.
 *
 * ⭐️ 실기기 QA 회귀 방지: 내비 라이브러리가 없어 ViewModelStore가 앱 전역이므로, key가 대상별로 유일하지
 * 않으면 처음 연 항목의 뷰모델이 재사용돼 다른 항목을 눌러도 첫 항목이 뜬다. 이 버그는 컴파일되고 모든
 * 화면 테스트를 통과했었다 — 그래서 "서로 다른 대상 → 서로 다른 key"를 여기서 순수 함수로 고정한다.
 */
class AppViewModelKeyTest {

    @Test
    fun 서로_다른_카테고리는_다른_키를_얻는다() {
        assertNotEquals(
            detailViewModelKey(Screen.CategoryHistoryDetail("DATA_STRUCTURE")),
            detailViewModelKey(Screen.CategoryHistoryDetail("NETWORK")),
        )
    }

    @Test
    fun 서로_다른_스크랩_문제는_다른_키를_얻는다() {
        assertNotEquals(
            detailViewModelKey(Screen.ScrapDetail(1L)),
            detailViewModelKey(Screen.ScrapDetail(2L)),
        )
    }

    @Test
    fun 서로_다른_이력_문제_상세는_다른_키를_얻는다() {
        // 같은 카테고리 안에서도 problemId가 다르면 키가 달라야 한다.
        assertNotEquals(
            detailViewModelKey(Screen.CategoryHistoryProblemDetail("DATA_STRUCTURE", 1L)),
            detailViewModelKey(Screen.CategoryHistoryProblemDetail("DATA_STRUCTURE", 2L)),
        )
    }

    @Test
    fun 서로_다른_신고_대상은_다른_키를_얻는다() {
        assertNotEquals(
            detailViewModelKey(Screen.Report(1L, ReportCategory.WRONG_ANSWER)),
            detailViewModelKey(Screen.Report(2L, ReportCategory.WRONG_ANSWER)),
        )
    }

    @Test
    fun 화면_종류가_다르면_키가_충돌하지_않는다() {
        // 접두사가 달라, 우연히 problemId·category 값이 겹쳐도 종류 간 키가 충돌하지 않는다.
        val keys = listOf(
            detailViewModelKey(Screen.ScrapDetail(1L)),
            detailViewModelKey(Screen.Report(1L, null)),
            detailViewModelKey(Screen.CategoryHistoryDetail("DATA_STRUCTURE")),
            detailViewModelKey(Screen.CategoryHistoryProblemDetail("DATA_STRUCTURE", 1L)),
        )
        assertEquals(keys.size, keys.toSet().size, "서로 다른 화면 종류의 키는 모두 유일하다")
    }

    @Test
    fun 단일_인스턴스_화면은_키가_없다() {
        // 홈·세션 등은 대상이 하나뿐이라 대상별 key가 필요 없다(클래스 단위 기본 캐시).
        assertNull(detailViewModelKey(Screen.Home))
        assertNull(detailViewModelKey(Screen.ScrapList))
        assertNull(detailViewModelKey(Screen.CategoryHistoryList))
    }
}
