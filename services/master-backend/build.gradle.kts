plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("tarotbot.integration-test")
    application
}

application {
    mainClass.set("com.tarotbot.masterbackend.MainKt")
}

dependencies {
    implementation(project(":proto"))
    implementation(project(":libs:resilience"))
    implementation(project(":libs:observability"))
    implementation(project(":libs:config"))
    implementation(project(":domain:tarot-domain"))
    implementation(project(":domain:tarot-render"))

    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)

    "integrationTestImplementation"(libs.grpc.testing)
    "integrationTestImplementation"(libs.grpc.inprocess)
}
