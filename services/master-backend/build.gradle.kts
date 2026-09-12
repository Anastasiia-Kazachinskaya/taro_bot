plugins {
    alias(libs.plugins.kotlin.jvm)
    id("tarotbot.integration-test")
}

dependencies {
    implementation(project(":proto"))
    implementation(project(":libs:resilience"))
    implementation(project(":libs:observability"))
    implementation(project(":libs:config"))
    implementation(project(":domain:tarot-domain"))
    implementation(project(":domain:tarot-render"))

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}

// State machine + orchestration implementation lands in a later phase.
