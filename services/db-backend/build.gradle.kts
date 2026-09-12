plugins {
    alias(libs.plugins.kotlin.jvm)
    id("tarotbot.integration-test")
}

dependencies {
    implementation(project(":proto"))
    implementation(project(":libs:resilience"))
    implementation(project(":libs:observability"))
    implementation(project(":libs:config"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)

    "integrationTestImplementation"(libs.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
    "integrationTestImplementation"(libs.grpc.testing)
}

// Flyway schema + DbService RPC implementation lands in a later phase.
