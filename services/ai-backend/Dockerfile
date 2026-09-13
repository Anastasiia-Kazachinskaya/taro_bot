# Build context is the repo root (multi-module Gradle build) — see docker-compose.yml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /repo
COPY . .
RUN ./gradlew :services:ai-backend:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /repo/services/ai-backend/build/install/ai-backend/ ./
EXPOSE 9092 9192
ENTRYPOINT ["/app/bin/ai-backend"]
