package watson.bytecs.config

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test

/**
 * db/migration의 모든 마이그레이션 SQL이 문법·순서 오류 없이 적용되는지 검증하는 스모크 테스트.
 *
 * 운영은 PostgreSQL이지만 이 환경에는 Docker(Testcontainers)가 없어, H2의 PostgreSQL 호환 모드로
 * 구문 수준까지만 검증한다. PostgreSQL 전용 구문(예: CREATE EXTENSION pg_trgm — 파이프라인 Phase 3 예정)이
 * 마이그레이션에 들어가는 시점에는 이 테스트를 Testcontainers 기반으로 승급해야 한다.
 * (일반 테스트는 flyway.enabled=false + ddl-auto create-drop이라 이 테스트가 유일한 마이그레이션 검증이다.)
 */
class FlywayMigrationSmokeTest {

    @Test
    fun 모든_마이그레이션이_PostgreSQL_호환_모드_H2에_적용된다() {
        val flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:flyway-smoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
            .locations("classpath:db/migration")
            .load()

        val result = flyway.migrate()

        assertThat(result.success).isTrue()
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1)
    }
}
