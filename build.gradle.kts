import org.gradle.api.tasks.testing.Test

plugins {
    kotlin("jvm") version "1.9.22"
    application
}

group = "space.norb"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    sourceSets {
        named("main") {
            kotlin.srcDirs(
                "src/main/kotlin",
                "vendor/llvm/src/main/kotlin",
                "vendor/kolor",
                "vendor/riscv-asm-kotlin/src/main/kotlin",
            )
        }
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

private fun Test.configureTestBehavior() {
    // Pass all system properties (like -Dname, -DirNoClang, -Doutput) to the test JVM
    systemProperties(System.getProperties().mapKeys { it.key.toString() })
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

tasks.test {
    useJUnitPlatform()
    configureTestBehavior()
    useJUnitPlatform {
        excludeTags("official", "fixed")
    }
}

private fun Array<String>.toLowerCamelCase(): String {
    return this.joinToString("") {
        it.lowercase().replaceFirstChar { ch ->
            ch.titlecase()
        }
    }.replaceFirstChar { it.lowercase() }
}

fun registerTask(stage: String, source: String) {
    val taskName = arrayOf(source, stage, "tests").toLowerCamelCase()
    tasks.register(taskName, Test::class.java) {
        description = "Run $source tests for the $stage stage."
        group = "verification"

        // Reuse the standard compiled test classes
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath

        configureTestBehavior()

        useJUnitPlatform {
            includeTags("$source&$stage")
        }
    }
}

for (stage in listOf("preprocessor", "lexer", "parser", "semantic", "ir")) {
    registerTask(stage, source = "manual")
}

for (stage in listOf("semantic", "ir", "asm")) {
    registerTask(stage, source = "official")
    if (stage != "asm") {
        registerTask(stage, source = "fixed")
    }
}
registerTask("opt", source = "official")

tasks.register<Test>("manualTests") {
    description = "Run manual tests."
    group = "verification"

    // Reuse the standard compiled test classes
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    configureTestBehavior()

    useJUnitPlatform {
        includeTags("manual")
    }
}

application {
    mainClass.set("rusty.MainKt")
}
