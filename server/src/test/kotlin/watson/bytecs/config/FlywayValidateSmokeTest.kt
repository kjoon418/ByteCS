package watson.bytecs.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * "Flyway가 만든 스키마 == Hibernate가 기대하는 스키마(validate)"를 검증하는 스모크 테스트.
 *
 * 일반 테스트는 flyway.enabled=false + ddl-auto=create-drop이라 엔티티에서 스키마를 재생성하므로,
 * 엔티티 변경이 V마이그레이션 없이 들어가는 회귀는 운영 기동(validate)에서만 드러난다(Phase 1 리뷰 지적).
 * 이 테스트는 운영과 같은 조합(Flyway 적용 → ddl-auto=validate 기동)을 H2 PostgreSQL 호환 모드에서 재현해
 * 컬럼 누락·타입 불일치를 조기에 잡는다. Docker(Testcontainers) 가동 시 실 Postgres로 승급할 것.
 */
@SpringBootTest(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.url=jdbc:h2:mem:flyway-validate-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    ],
)
class FlywayValidateSmokeTest {

    @Test
    fun `Flyway가 만든 스키마로 validate 모드 기동이 성공한다`() {
        // 컨텍스트 기동 자체가 검증이다 — 마이그레이션 산출 스키마와 엔티티 매핑이 어긋나면 기동이 실패한다.
    }
}
