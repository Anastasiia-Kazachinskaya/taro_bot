plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("tarotbot.integration-test")
}

dependencies {
    implementation(project(":proto"))
    implementation(project(":libs:resilience"))
    implementation(project(":libs:observability"))
    implementation(project(":libs:config"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.grpc.netty)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)

    "integrationTestImplementation"(libs.testcontainers)
    "integrationTestImplementation"(libs.testcontainers.postgresql)
    "integrationTestImplementation"(libs.testcontainers.junit.jupiter)
    "integrationTestImplementation"(libs.grpc.testing)
    "integrationTestImplementation"(libs.grpc.inprocess)
}
