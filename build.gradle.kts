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
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

// On-demand official tests use the standard test sourceSet but are tagged
tasks.register<Test>("officialTest") {
    description = "Run official semantic tests (on-demand)."
    group = "verification"
    // Reuse compiled test classes and classpath
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("official")
    }
    shouldRunAfter(tasks.test)
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
    // Do not run @Tag("official") tests by default; run via :officialTest
    useJUnitPlatform {
        excludeTags("official")
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
