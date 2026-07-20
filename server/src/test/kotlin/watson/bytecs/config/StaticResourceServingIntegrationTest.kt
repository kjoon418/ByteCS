package watson.bytecs.config

import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * 웹 클라이언트 번들의 같은 오리진 서빙을 위한 정적 리소스 permit 규칙을 검증한다.
 *
 * 실제 번들은 -PincludeWeb 빌드에서만 static/에 들어가므로, 여기서는 시큐리티 규칙 자체를 본다:
 *  - 정적 자산 경로(GET)는 인증 없이 열린다(웰컴 페이지 index.html은 테스트 fixture로 확인).
 *  - permit은 GET·정적 경로로 한정되어, /api 보호와 서빙 외 표면은 그대로다(회귀 가드).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticResourceServingIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `루트는 인증 없이 웹 클라이언트 index로 라우팅된다`() {
        // 웰컴 페이지는 index.html로 forward하므로(본문은 리소스 핸들러가 낸다) 여기선 접근 허용(200)만 본다.
        mockMvc.get("/")
            .andExpect {
                status { isOk() }
            }
    }

    @Test
    fun `index_html은 인증 없이 웹 클라이언트 문서를 서빙한다`() {
        mockMvc.get("/index.html")
            .andExpect {
                status { isOk() }
                content { string(containsString("bytecs-web-test-fixture")) }
            }
    }

    @Test
    fun `루트 레벨 js·wasm·css·composeResources 경로는 permit되어 인증을 요구하지 않는다`() {
        // 번들이 테스트 리소스에 없어 자원은 없지만(실서버에선 404), 핵심은 "미인증 401이 아니다"이다.
        // 미permit 경로라면 JWT 진입점이 401을 냈을 것이다 — 401이 아니라는 것이 permit의 증거다.
        for (path in listOf("/bytecs.js", "/app.wasm", "/styles.css", "/composeResources/font/x.ttf")) {
            val status = mockMvc.get(path).andReturn().response.status
            assertNotEquals(401, status, "$path 는 정적 permit이라 인증(401)을 요구하지 않아야 한다")
        }
    }

    @Test
    fun `보호된 API는 정적 permit 추가 후에도 여전히 401이다`() {
        // 정적 permit이 /api 보호를 열지 않았음을 못박는다(회귀 가드).
        mockMvc.get("/api/users/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.errorCode") { value("UNAUTHORIZED") }
            }
    }

    @Test
    fun `정적 permit은 GET 전용이라 POST 루트는 인증을 요구한다`() {
        // GET "/"만 열려 있어 POST "/"는 anyRequest authenticated로 떨어진다 — 서빙 외 쓰기 표면을 만들지 않는다.
        mockMvc.post("/")
            .andExpect {
                status { isUnauthorized() }
            }
    }
}
