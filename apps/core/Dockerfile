# Build from the repository root: docker build -f apps/core/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts ./
COPY gradle gradle
COPY apps/core apps/core
COPY packages/plans/plans.json packages/plans/plans.json
RUN ./gradlew :core:bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /src/apps/core/build/libs/*.jar app.jar
COPY --from=build /src/apps/core/build/otel/opentelemetry-javaagent.jar otel/opentelemetry-javaagent.jar
EXPOSE 8080
# OpenTelemetry agent (ADR-0012): inert while OTEL_SDK_DISABLED=true; configured with OTEL_* env vars.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-javaagent:/app/otel/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
