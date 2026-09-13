# Build context is the repo root (multi-module Gradle build) — see docker-compose.yml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /repo
COPY . .
RUN ./gradlew :services:master-backend:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /repo/services/master-backend/build/install/master-backend/ ./
EXPOSE 9090 9190
ENTRYPOINT ["/app/bin/master-backend"]
