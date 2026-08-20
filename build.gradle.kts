plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// Zero-reflection guarantee: exclude kotlin-reflect from runtime dependency configurations.
// Scoped to published configurations so compiler toolchain internals are not affected.
subprojects {
    listOf(
        "api",
        "implementation",
        "runtimeOnly",
        "compileOnly",
    ).forEach { name ->
        configurations.matching { it.name.startsWith(name) }.configureEach {
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        }
    }
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    // ForbiddenImport is syntactic, analyzing commonMain and platform source sets.
    source.setFrom(files("composeshield/src", "sample"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
