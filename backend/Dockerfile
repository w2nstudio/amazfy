# Backend container image. Build from the REPOSITORY ROOT (the build needs api/openapi.yaml):
#   docker build -f backend/Dockerfile -t amazfy-backend .
# Run (production profile; settings come from environment variables):
#   docker run -p 8080:8080 -e DB_URL=... -e DB_USERNAME=... -e DB_PASSWORD=... -e CORS_ALLOWED_ORIGINS=... amazfy-backend

# ---- Stage 1: build the jar with a full JDK ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY api/ api/
COPY backend/ backend/
WORKDIR /workspace/backend
# Tests run in CI, not here (they need Docker for Testcontainers). The cache mount keeps Gradle downloads between builds.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain
# Split the jar into layers (libraries change rarely, our code often) so image updates stay small
RUN java -Djarmode=tools -jar build/libs/amazfy-backend.jar extract --layers --launcher --destination /workspace/extracted

# ---- Stage 2: run with a small runtime-only image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as an unprivileged user, never as root
RUN groupadd --system amazfy && useradd --system --gid amazfy --no-create-home amazfy
USER amazfy
COPY --from=build --chown=amazfy:amazfy /workspace/extracted/dependencies/ ./
COPY --from=build --chown=amazfy:amazfy /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=amazfy:amazfy /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=amazfy:amazfy /workspace/extracted/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod
# Let the JVM use up to 75% of the container's memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
