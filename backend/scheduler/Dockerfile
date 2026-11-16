# syntax=docker/dockerfile:1
FROM eclipse-temurin:27-jdk AS build
WORKDIR /workspace
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN --mount=type=cache,target=/root/.gradle sh ./gradlew --no-daemon bootJar -x test \
    && cp build/libs/*.jar /workspace/application.jar

FROM eclipse-temurin:27-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/application.jar ./application.jar
ENV SPRING_DOCKER_COMPOSE_ENABLED=false
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
    CMD curl --fail --silent http://127.0.0.1:8080/api/schedulings/task-types > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
