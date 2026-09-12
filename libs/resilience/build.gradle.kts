plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.grpc.netty)
    api(libs.grpc.stub)
    api(libs.grpc.protobuf)
    implementation(libs.resilience4j.circuitbreaker)
    implementation(libs.resilience4j.kotlin)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
