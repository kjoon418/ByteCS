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
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    runtimeOnly(libs.postgresql)
    // Docker가 없는 환경에서 local 프로파일 구동 및 통합 테스트에 쓰는 인메모리 DB.
    // 런타임 기본(운영)은 여전히 Postgres이며, local 프로파일에서만 H2를 사용한다.
    // TODO: Testcontainers(실제 Postgres)로 승급 — Docker 가동 시
    runtimeOnly(libs.h2)
    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
