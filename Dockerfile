# 테스터 배포용 런타임 이미지. jar는 CI(GitHub Actions)에서 미리 빌드한다 —
# Gradle+wasm 빌드는 힙 4GB급이라 무료 호스팅의 빌드 환경에서 돌리지 않는다.
# 사용법: gradlew -PincludeWeb :server:bootJar 후 docker build .
FROM eclipse-temurin:21-jre-alpine

# Render 무료 인스턴스(RAM 512MB) 기준 힙 상한. 배포 환경변수 JAVA_TOOL_OPTIONS로 재정의 가능.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+UseSerialGC"

COPY server/build/libs/server-1.0.0.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
