import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    jacoco
}

group = "io.github.jorgetroya80"
// version comes from gradle.properties (managed by release-please)

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

repositories {
    mavenCentral()
}

// Smoke test against the real EMT: own source set (no test resources, so no fake credentials)
val smokeTest: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations[smokeTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[smokeTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
                .get()
                .toString(),
        )
        mavenBom(
            libs.springdoc.bom
                .get()
                .toString(),
        )
    }
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.resilience4j.spring.boot4)
    implementation(libs.caffeine)
    // @ApplicationModule and @PackageInfo annotations
    implementation(libs.spring.modulith.api)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)
    // OpenAPI annotations on controllers; springdoc itself only runs in bootRun and tests (never in the jar)
    compileOnly(libs.swagger.annotations.jakarta)
    developmentOnly(libs.springdoc.openapi.starter.webmvc.ui)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.mockk)
    testImplementation(libs.testcontainers)
    testImplementation(libs.springdoc.openapi.starter.webmvc.ui)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// META-INF/build-info.properties: application.yaml imports it to put the version in the OpenAPI document.
// Without the build time, so the file only changes with the version
springBoot {
    buildInfo {
        excludes.set(setOf("time"))
    }
}

// Only the executable jar is needed (Docker image)
tasks.jar {
    enabled = false
}

tasks.test {
    // The Docker image test is slow and needs Docker, and the OpenAPI export writes a file: own tasks
    useJUnitPlatform { excludeTags("docker", "openapi") }
    // Full failure details in the console, so CI logs are enough to diagnose
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

val dockerImageTest by tasks.registering(Test::class) {
    description = "Builds api/Dockerfile and checks the container (needs Docker)."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("docker") }
    // The test runs `docker build .` from the project directory
    workingDir = projectDir
    shouldRunAfter(tasks.test)
}

val generateOpenApi by tasks.registering(Test::class) {
    description = "Writes the OpenAPI document of the public API to build/openapi/bicimad.json (no EMT needed)."
    group = "documentation"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("openapi") }
    val document = layout.buildDirectory.file("openapi/bicimad.json")
    outputs.file(document)
    systemProperty("openapi.output", document.get().asFile.path)
}

// SC10: springdoc and Swagger UI are for development only, never in the production jar
val verifyNoSpringdocInJar by tasks.registering {
    description = "Fails if bootJar contains springdoc or Swagger."
    group = "verification"
    val jar = tasks.bootJar.flatMap { it.archiveFile }
    inputs.file(jar)
    val forbiddenNames = listOf("springdoc", "swagger")
    doLast {
        val forbidden =
            ZipFile(jar.get().asFile).use { zip ->
                zip
                    .entries()
                    .asSequence()
                    .map { it.name }
                    .filter { name -> forbiddenNames.any(name::contains) }
                    .toList()
            }
        check(forbidden.isEmpty()) { "bootJar must not contain springdoc or Swagger:\n${forbidden.joinToString("\n")}" }
    }
}

val smokeTestTask =
    tasks.register<Test>("smokeTest") {
        description = "Calls the real EMT with the credentials in .env.local (manual, never in CI)."
        group = "verification"
        testClassesDirs = smokeTest.output.classesDirs
        classpath = smokeTest.runtimeClasspath
        useJUnitPlatform()
        // application.yaml imports .env.local relative to the working directory
        workingDir = projectDir
        // Always hit the EMT when asked to, even if nothing changed
        outputs.upToDateWhen { false }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

// Coverage gates (spec: at least 80 % of lines in `shared` and in each domain module)
fun JacocoCoverageVerification.requireLineCoverage(packagePath: String) {
    dependsOn(tasks.test)
    executionData(tasks.test.get())
    sourceDirectories.setFrom(
        sourceSets.main
            .get()
            .allSource.srcDirs,
    )
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs
            .asFileTree
            .matching { include("io/github/jorgetroya80/madmobility/$packagePath/**") },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.jacocoTestCoverageVerification {
    requireLineCoverage("shared")
}

val bicimadCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    description = "Checks line coverage of modules/bicimad."
    group = "verification"
    requireLineCoverage("modules/bicimad")
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification, bicimadCoverageVerification, verifyNoSpringdocInJar)
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
