import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // Principle VI: anything not deliberately public is internal. Strict mode additionally
    // requires explicit return types, so the published surface can never widen by inference.
    explicitApi()

    android {
        namespace = "io.github.composeguard"
        compileSdk =
            libs.versions.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.minSdk
                .get()
                .toInt()

        // Both test source sets are disabled by default under the AGP KMP library plugin and
        // need explicit opt-in. Names differ from the legacy layout: androidHostTest, not
        // androidUnitTest (research.md R9).
        withHostTest {
            // Robolectric needs the merged resources and the generated R classes of the library's
            // transitive dependencies. Without this, composing anything fails at runtime with
            // NoClassDefFoundError on androidx.customview.poolingcontainer.R$id.
            isIncludeAndroidResources = true
        }
        withDeviceTest {}
    }

    // CMP 1.11 removed iosX64 — declaring it would fail to resolve (research.md R9).
    iosArm64()
    iosSimulatorArm64()

    // Calling this block is what enables ABI validation — there is no longer an `enabled` flag.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        // Deliberate: the default `true` lets KGP *infer* Apple ABI when those targets cannot be
        // compiled locally, which can mask a genuinely binary-incompatible change. Principle VI
        // treats the dump as the compatibility boundary, so inference is unacceptable — the check
        // runs on macOS where Apple targets actually compile (research.md R10).
        keepLocallyUnsupportedTargets.set(false)

        filters {
            exclude {
                annotatedWith.add("io.github.composeguard.InternalComposeGuardApi")
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
