# syntax=docker/dockerfile:1.7
# Multi-stage build for Spring Boot on Java 21

# ---------- Stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Gradle wrapper + build descriptors first for better layer caching
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x ./gradlew

# Warm dependency cache (no source yet -> only re-runs when descriptors change)
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now bring in sources and build the fat jar (skip tests in image build)
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
