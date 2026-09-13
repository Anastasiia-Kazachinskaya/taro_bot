# Build context is the repo root (multi-module Gradle build) — see docker-compose.yml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /repo
COPY . .
RUN ./gradlew :services:db-backend:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /repo/services/db-backend/build/install/db-backend/ ./
EXPOSE 9091 9191
ENTRYPOINT ["/app/bin/db-backend"]
