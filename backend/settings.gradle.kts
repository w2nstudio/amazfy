plugins {
    // Downloads the JDK version set in build.gradle.kts (toolchain) if it isn't installed
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
