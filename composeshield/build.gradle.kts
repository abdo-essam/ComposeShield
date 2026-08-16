import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
}

group = "io.github.abdo-essam"
version = "0.1.0"

mavenPublishing {
    publishToMavenCentral()
    if (project.hasProperty("signing.keyId") || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }
    coordinates("io.github.abdo-essam", "composeshield", "0.1.0")

    pom {
        name.set("ComposeShield")
        description.set("Screen capture protection for Compose Multiplatform")
        inceptionYear.set("2026")
        url.set("https://github.com/abdo-essam/ComposeShield")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("abdo-essam")
                name.set("Abdelrahman Essam")
                url.set("https://github.com/abdo-essam")
            }
        }
        scm {
            url.set("https://github.com/abdo-essam/ComposeShield")
            connection.set("scm:git:git://github.com/abdo-essam/ComposeShield.git")
            developerConnection.set("scm:git:ssh://git@github.com/abdo-essam/ComposeShield.git")
        }
    }
}

kotlin {
    // Principle VI: anything not deliberately public is internal. Strict mode additionally
    // requires explicit return types, so the published surface can never widen by inference.
    explicitApi()

    android {
        namespace = "io.github.composeshield"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        withHostTest {
            // Robolectric needs merged resources and generated R classes of transitive dependencies.
            isIncludeAndroidResources = true
        }
        withDeviceTest {}
    }

    // CMP 1.11 removed iosX64 — declaring it would fail to resolve (research.md R9).
    iosArm64 {
        binaries {
            framework {
                baseName = "ComposeShield"
                isStatic = true
            }
        }
    }
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "ComposeShield"
                isStatic = true
            }
        }
    }

    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        keepLocallyUnsupportedTargets.set(false)

        filters {
            exclude {
                annotatedWith.add("io.github.composeshield.InternalComposeShieldApi")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.compose.ui.test)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }

        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
