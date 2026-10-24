import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    jacoco
}

group = "uk.me.michael-lloyd-lee"
version = "0.1.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
}

val javaToolchains = extensions.getByType<JavaToolchainService>()

tasks.withType<Detekt>().configureEach {
    jdkHome.set(
        javaToolchains
            .launcherFor { languageVersion.set(JavaLanguageVersion.of(17)) }
            .map { it.metadata.installationPath },
    )
    jvmTarget = "17"
    languageVersion = "2.2"
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
    finalizedBy(tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification"))
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("ktlintCheck"))
    dependsOn(tasks.named("detekt"))
    dependsOn(tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification"))
}
