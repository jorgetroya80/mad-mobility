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

dependencyManagement {
    imports {
        mavenBom(
            libs.spring.modulith.bom
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

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Only the executable jar is needed (Docker image)
tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    // Full failure details in the console, so CI logs are enough to diagnose
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

// Coverage gate for the shared infrastructure (spec: at least 80 % of lines in `shared`)
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main
            .get()
            .output.classesDirs
            .asFileTree
            .matching { include("io/github/jorgetroya80/madmobility/shared/**") },
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

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
