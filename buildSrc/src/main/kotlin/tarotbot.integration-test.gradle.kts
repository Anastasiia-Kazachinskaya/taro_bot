import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.register

// Reusable convention: adds a `src/integrationTest/kotlin` source set backed by its
// own `integrationTest` Gradle task, sharing `main`+`test` output/classpath so
// integration tests can reuse test fixtures without duplicating dependencies.
// Applied by services that need Testcontainers/in-process gRPC integration tests
// (db-backend, master-backend, ai-backend) — kept separate from `test` so CI can
// report them distinctly and so plain unit tests stay fast.

val sourceSets = extensions.getByType(SourceSetContainer::class)
val main by sourceSets.getting
val test by sourceSets.getting

// Kotlin's JVM plugin configures `src/<sourceSetName>/kotlin` as a source
// directory by convention for every registered SourceSet, so no explicit
// `.kotlin.srcDir(...)` call is needed here (same for `resources`).
val integrationTest by sourceSets.creating {
    compileClasspath += main.output + test.output
    runtimeClasspath += output + compileClasspath
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.getByName("testImplementation"))
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.getByName("testRuntimeOnly"))
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests (Testcontainers / in-process gRPC)."
    group = "verification"
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter("test")
}

tasks.named("check") {
    dependsOn(integrationTestTask)
}
