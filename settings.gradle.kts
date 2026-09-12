rootProject.name = "tarot-bot"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    "proto",
    "libs:resilience",
    "libs:observability",
    "libs:config",
    "domain:tarot-domain",
    "domain:tarot-render",
    "services:gateway",
    "services:master-backend",
    "services:db-backend",
    "services:ai-backend",
)
