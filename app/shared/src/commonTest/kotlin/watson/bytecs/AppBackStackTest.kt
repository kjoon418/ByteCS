package watson.bytecs

import watson.bytecs.session.CompletionSummary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 명시적 백스택(내비 라이브러리 없음) 조작의 불변식을 Compose 트리 밖에서 못박는다([rootPhase] 가드와 같은 결).
 *
 * ⭐️ 핵심 불변식(실기기 QA): '조금 더 풀기'로 완료↔세션을 반복해도 완료 화면([Screen.SessionComplete])은
 * 홈 바로 위 하나만 유지된다. 누적되면 '오늘은 여기까지'·하드웨어 뒤로가기 한 번으로 홈에 닿지 못하고
 * 이전 완료 화면이 다시 보인다.
 */
class AppBackStackTest {

    private fun completion() =
        Screen.SessionComplete(CompletionSummary(solvedCount = 3, totalCount = 3, streak = null))

    /** '조금 더 풀기'를 여러 번 반복해도 완료 화면이 백스택에 쌓이지 않는다(onMore=완료 pop 후 세션 push). */
    @Test
    fun 조금_더_풀기를_반복해도_완료_화면이_누적되지_않는다() {
        val stack = mutableListOf<Screen>(Screen.Home)

        // 첫 세션 시작 → 완료(onCompleted: 세션 pop 후 완료 push).
        stack.pushDistinct(Screen.Session())
        stack.popScreen()
        stack.pushDistinct(completion())
        assertEquals(listOf(Screen.Home, completion()), stack)

        // '조금 더 풀기' 3회 반복.
        repeat(3) {
            // onMore: 완료 화면을 걷어낸 뒤 새 세션으로 재진입.
            stack.popScreen()
            stack.pushDistinct(Screen.Session(startNext = true))
            // onCompleted: 세션 pop 후 완료 push.
            stack.popScreen()
            stack.pushDistinct(completion())
        }

        // 완료 화면은 홈 바로 위 하나만 유지된다 → '오늘은 여기까지' 한 번이면 홈.
        assertEquals(listOf(Screen.Home, completion()), stack)
    }

    /** 뒤로가기(pop)는 홈(밑바닥) 밑으로 내려가지 않는다 — 막다른 길 방지. */
    @Test
    fun 뒤로가기는_홈_밑으로_내려가지_않는다() {
        val stack = mutableListOf<Screen>(Screen.Home)
        stack.popScreen()
        assertEquals(listOf<Screen>(Screen.Home), stack)

        stack.pushDistinct(Screen.Account)
        stack.popScreen()
        assertEquals(listOf<Screen>(Screen.Home), stack)
    }

    /** 같은 화면은 중복 push되지 않는다(더블 탭 방어). */
    @Test
    fun 같은_화면은_중복_push되지_않는다() {
        val stack = mutableListOf<Screen>(Screen.Home)
        stack.pushDistinct(Screen.Account)
        stack.pushDistinct(Screen.Account)
        assertEquals(listOf(Screen.Home, Screen.Account), stack)
    }
}
