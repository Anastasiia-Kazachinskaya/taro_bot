# Build context is the repo root (multi-module Gradle build) — see docker-compose.yml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /repo
COPY . .
RUN ./gradlew :services:gateway:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /repo/services/gateway/build/install/gateway/ ./
EXPOSE 9100 9101
ENTRYPOINT ["/app/bin/gateway"]
