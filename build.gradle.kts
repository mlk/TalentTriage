import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.shadow)
    application
    jacoco
}

group = "uk.me.michael-lloyd-lee"
version = "0.1.0"

application {
    applicationName = "recruiter-cli"
    mainClass.set("uk.me.michaellloydlee.recruiter.cli.RecruiterCliKt")
}

// The standalone jar of the application.
//
// The jar carries the dependencies with it, so `java -jar` runs it without a
// class path. The name has no version in it, the launchd configuration of
// macOS points at the file and a new release does not move it.
tasks.shadowJar {
    archiveFileName.set("recruiter-cli.jar")
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
        )
    }
    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "module-info.class",
    )
    // The service descriptors and the module metadata of the dependencies are
    // merged into one file, so every provider and every module stays in the
    // jar. The duplicates of the other files are dropped, the first one wins.
    filesMatching(listOf("META-INF/services/**", "META-INF/*.kotlin_module")) {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
}

// The launchd configuration of macOS is a sample of the repository, it is not
// a resource of the application. The test reads it from the class path, so it
// does not depend on the working directory of the build.
sourceSets.named("test") {
    resources.srcDir("config/launchd")
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox)
    implementation(libs.poi.ooxml)

    runtimeOnly(libs.slf4j.nop)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
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
    toolVersion = libs.versions.jacoco.get()
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
