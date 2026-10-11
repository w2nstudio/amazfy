plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.openapi.generator") version "7.26.0"
}

group = "com.amazfy"
version = "0.0.1-SNAPSHOT"
description = "Amazfy backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// JPA needs entity classes to be open (Kotlin classes are final by default)
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// One executable jar with a fixed name (used by the Dockerfile); no extra "plain" jar
tasks.bootJar {
    archiveFileName = "amazfy-backend.jar"
}
tasks.jar {
    enabled = false
}

// Writes build-info.properties (name, version, build time) so the app can report its own version
springBoot {
    buildInfo {
        properties {
            name = "amazfy-backend"
        }
    }
}

// --- API contract: generate Kotlin interfaces from ../api/openapi.yaml ---
// Controllers implement the generated interfaces, so code that doesn't match the contract won't compile.
val generatedApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName = "kotlin-spring"
    inputSpec = layout.projectDirectory.file("../api/openapi.yaml")
    outputDir = generatedApiDir
    apiPackage = "com.amazfy.api.generated"
    modelPackage = "com.amazfy.api.generated.model"
    configOptions =
        mapOf(
            "useSpringBoot4" to "true",
            "interfaceOnly" to "true", // only interfaces; we write the controllers
            "skipDefaultInterface" to "true", // no default bodies: every endpoint must be implemented
            "useTags" to "true", // one interface per tag, e.g. SystemApi
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "annotationLibrary" to "none",
            "exceptionHandler" to "false",
            "gradleBuildFile" to "false",
            "requestMappingMode" to "none",
        )
}

sourceSets.main {
    kotlin.srcDir(generatedApiDir.map { it.dir("src/main/kotlin") })
}

tasks.compileKotlin { dependsOn(tasks.openApiGenerate) }

// Generated code follows the generator's style, not ours
ktlint {
    filter { exclude { it.file.path.contains("generated") } }
}
tasks.matching { it.name.startsWith("runKtlint") }.configureEach { dependsOn(tasks.openApiGenerate) }
