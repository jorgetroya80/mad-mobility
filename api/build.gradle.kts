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
    // @ApplicationModule and @PackageInfo annotations
    implementation(libs.spring.modulith.core)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.actuator.test)
    testImplementation(libs.spring.modulith.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Only the executable jar is needed (Docker image)
tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}
