import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "space.norb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test")) // Kotlin test assertions
    // Explicit JUnit Jupiter dependencies to avoid deprecated automatic framework loading
    val junitVersion = "5.10.2"
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        val localFile = System.getProperty("localTestFile")
        if (localFile != null) {
            showStandardStreams = true
        }
    }
    doFirst {
        val localFile = System.getProperty("localTestFile")
        if (localFile != null) {
            systemProperty("localTestFile", localFile)
            val localMode = System.getProperty("localTestMode")
            if (localMode != null) systemProperty("localTestMode", localMode)
        }
    }
    // If a local manual test file is specified, always rerun tests to show fresh dump output.
    if (System.getProperty("localTestFile") != null) {
        outputs.upToDateWhen { false }
    }
}

application {
    mainClass.set("rusty.MainKt")
}
