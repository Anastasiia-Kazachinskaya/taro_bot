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

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kaml)
    implementation(libs.grpc.netty)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)

    "integrationTestImplementation"(libs.ktor.client.mock)
    "integrationTestImplementation"(libs.grpc.testing)
    "integrationTestImplementation"(libs.grpc.inprocess)
}
