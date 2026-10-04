# syntax=docker/dockerfile:1
# Build:  docker build -t mad-mobility-api api
# Run:    docker run --rm -p 8080:8080 --env-file api/.env.local mad-mobility-api

# --- Build: compile the executable jar with the Gradle wrapper ---
FROM eclipse-temurin:25-jdk@sha256:8c0a84ea11c8f6ed52600fc19f1040121f2a162998e9f50a5faebbbad9172dcc AS build
WORKDIR /workspace

# Build scripts first, so dependency resolution is cached until they change
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --quiet > /dev/null

# Tests run in CI; the image only needs the jar
COPY src/main src/main
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --quiet \
 && java -Djarmode=tools -jar build/libs/mad-mobility-api-*.jar extract --layers --application-filename app.jar --destination /workspace/extracted

# --- Runtime: JRE only, non-root, Spring Boot layers from least to most frequently changing ---
FROM eclipse-temurin:25-jre@sha256:fcd7fd7b387f94bb2ac461478a7436ad8e349924c374ea8313919624dceae636
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

# Files stay owned by root: the app user can read them but not modify them
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER 10001:10001
EXPOSE 8080
# Size the heap from the container memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# No HEALTHCHECK: the JRE image has no curl/wget; orchestrators probe /health/live and /health/ready
ENTRYPOINT ["java", "-jar", "app.jar"]
