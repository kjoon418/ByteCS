import org.gradle.language.jvm.tasks.ProcessResources

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
    // 관리자 페이지(/admin/**)는 서버 렌더링 웹(내부 도구)이다 — 문제 콘텐츠 파이프라인 구현 계획 §5.
    implementation(libs.spring.boot.starter.thymeleaf)
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

// 웹 클라이언트(wasmJs) 번들을 같은 오리진에서 서빙하기 위해 정적 리소스(static/)로 포함한다.
// ⚠️ -PincludeWeb 게이트 뒤에 둔다. 게이트 없이 processResources에 걸면 일상 서버 개발 루프
//    (:server:test·:server:bootRun)이 매번 무거운 웹 번들 빌드를 기다리게 된다.
// 사용법:
//   gradlew -PincludeWeb :server:bootRun   (웹 포함 통합 확인)
//   gradlew -PincludeWeb :server:bootJar    (웹+API 단일 배포 아티팩트)
// 게이트 없는 :server:test·:server:bootRun 은 웹 빌드 없이 기존 속도로 돈다.
if (project.hasProperty("includeWeb")) {
    val webDist = project(":app:webApp").layout.buildDirectory
        .dir("dist/wasmJs/productionExecutable")
    tasks.named<ProcessResources>("processResources") {
        dependsOn(":app:webApp:wasmJsBrowserDistribution")
        from(webDist) {
            into("static")
        }
    }
}
