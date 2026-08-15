plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

// The unsuppressable half of the zero-reflection guarantee (Principle V, research.md R12).
// Detekt's ForbiddenImport rule can be suppressed with an annotation; a missing artifact cannot.
//
// Scoped to the configurations that describe what the library *ships* and what a consumer would
// resolve, deliberately NOT `configurations.configureEach`. The broad form also strips
// kotlin-reflect from the Kotlin compiler's own Build Tools API classpath, which needs reflection
// to run — the build then fails with `NoClassDefFoundError: kotlin/reflect/full/KClasses` from
// inside the compiler, far from any clue about its cause. The guarantee is about the runtime
// dependency graph; the toolchain that compiles it is not part of that promise.
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
    // ForbiddenImport is syntactic, so unlike ForbiddenMethodCall it analyses commonMain too.
    // Type-resolution rules only run on JVM source sets and would silently skip shared code
    // (detekt #7073) — the zero-reflection gate therefore has to be syntactic. See research.md R12.
    source.setFrom(files("composeshield/src", "sample"))
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
}
