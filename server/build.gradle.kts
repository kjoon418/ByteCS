plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.kotlinJpa)
    alias(libs.plugins.springBoot)
    alias(libs.plugins.springDependencyManagement)
}

group = "watson.bytecs"
version = "1.0.0"

dependencies {
    api(projects.core)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)
    runtimeOnly(libs.postgresql)
    // 운영 스키마는 Flyway 마이그레이션(db/migration)이 단일 출처다. ddl-auto는 validate로만 쓴다.
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgresql)
    // H2는 local 프로파일 구동(bootRun)과 통합 테스트에만 쓰는 인메모리 DB.
    // testAndDevelopmentOnly: bootRun과 테스트 클래스패스에는 포함되지만,
    // 최종 bootJar(프로덕션 아티팩트)에는 포함되지 않아 운영 배포를 오염시키지 않는다.
    // 운영 런타임 기본은 여전히 Postgres.
    // TODO: Testcontainers(실제 Postgres)로 승급 — Docker 가동 시
    testAndDevelopmentOnly(libs.h2)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
