import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.time.Instant

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
}

group = "io.github.abdo-essam"
version = "0.1.0"

mavenPublishing {
    publishToMavenCentral()
    if (project.hasProperty("signing.keyId") || System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
        signAllPublications()
    }

    // Coordinates default to the project's group / name / version — a single source of truth.

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
                name.set("Abdo Essam")
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
    // Anything not deliberately public is internal. Strict mode additionally
    // requires explicit return types, so the published surface can never widen by inference.
    explicitApi()

    jvmToolchain(21)

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

    // CMP 1.11 removed iosX64 target.
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

        // androidDeviceTest — instrumentation tests for FTL physical device validation
        getByName("androidDeviceTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.rules)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.androidx.test.uiautomator)
        }
    }
}

// ---------------------------------------------------------------------------
// Dokka strict mode — fails on any public declaration missing KDoc
// ---------------------------------------------------------------------------
tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets.configureEach {
        failOnWarning.set(true)
    }
}

// Workaround for Compose Multiplatform plugin issue with Android KMP device tests
tasks.matching { it.name.contains("ComposeResourcesToAndroidAssets") }.configureEach {
    try {
        val method = this.javaClass.methods.find { it.name == "getOutputDirectory" }
        val prop = method?.invoke(this)
        if (prop is org.gradle.api.file.DirectoryProperty && !prop.isPresent) {
            prop.set(layout.buildDirectory.dir("intermediates/compose-resources/$name"))
        }
    } catch (_: Throwable) {
        // Ignore if reflection fails
    }
}

// ---------------------------------------------------------------------------
// generateValidationReport task
// Reads JUnit XML from FTL results + config/test-id-map.yml, emits:
//   build/reports/validation-report.json  (conforms to contracts/validation-report.schema.json)
//   build/reports/validation-report.html  (human-readable)
// ---------------------------------------------------------------------------
val generateValidationReport by tasks.registering {
    group = "verification"
    description = "Generates a machine-readable validation report from JUnit XML + test-id-map.yml"

    val junitXmlDir = layout.buildDirectory.dir("ftl-results")
    val testIdMapFile = rootProject.file("config/test-id-map.yml")
    val manualResultsFile = rootProject.file("build/manual-required-results.json")
    val outputDir = layout.buildDirectory.dir("reports")

    inputs.files(testIdMapFile)
    inputs.files(manualResultsFile)
    inputs.files(junitXmlDir)
    outputs.dir(outputDir)

    doLast {
        val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: "abdo-essam/ComposeShield"
        val rawSha = System.getenv("GITHUB_SHA")
        val commitSha =
            if (!rawSha.isNullOrBlank() && rawSha.matches(Regex("^[0-9a-f]{40}$"))) {
                rawSha
            } else {
                "0000000000000000000000000000000000000000"
            }
        val rawRunId = System.getenv("GITHUB_RUN_ID")
        val runId =
            if (!rawRunId.isNullOrBlank() && rawRunId.matches(Regex("^[0-9]+$"))) {
                rawRunId
            } else {
                "0"
            }
        val triggerType = if (System.getenv("TRIGGER_TYPE") == "release") "release" else "on-demand"
        val timestamp = Instant.now().toString()

        // Parse test-id-map.yml (simple line-based — avoids external YAML dependency)
        val testIdMap = mutableMapOf<String, Map<String, String>>()
        if (testIdMapFile.exists()) {
            var currentId: String? = null
            val entry = mutableMapOf<String, String>()
            testIdMapFile.readLines().forEach { line ->
                val trimmed = line.trimStart()
                val idMatch = Regex("^([A-Z]-[0-9]{3}):").find(trimmed)
                if (idMatch != null) {
                    currentId?.let { testIdMap[it] = entry.toMap() }
                    entry.clear()
                    currentId = idMatch.groupValues[1]
                } else if (currentId != null && trimmed.contains(":")) {
                    val (k, v) = trimmed.split(":", limit = 2)
                    entry[k.trim()] = v.trim().removeSurrounding("\"")
                }
            }
            currentId?.let { testIdMap[it] = entry.toMap() }
        }

        // Parse JUnit XML results
        val passedTests = mutableSetOf<String>()
        val failedTests = mutableMapOf<String, String>() // testId -> failureReason
        val xmlDir = junitXmlDir.get().asFile
        if (xmlDir.exists()) {
            xmlDir.walkTopDown().filter { it.extension == "xml" }.forEach { xmlFile ->
                val content = xmlFile.readText()
                // Match <testcase ...> blocks regardless of attribute order
                Regex("""<testcase\b([^>]+)""").findAll(content).forEach { m ->
                    val attrs = m.groupValues[1]
                    // The \b anchors are load-bearing: without one, `name=` also matches inside
                    // `classname=`, every method resolves to the class FQN, and no testcase ever
                    // matches the id-map — every test would report "blocked".
                    val fullClass =
                        Regex("""\bclassname=["']([^"']+)["']""").find(attrs)?.groupValues?.get(1) ?: ""
                    val method =
                        Regex("""\bname=["']([^"']+)["']""").find(attrs)?.groupValues?.get(1) ?: ""
                    if (fullClass.isBlank() || method.isBlank()) return@forEach

                    // Match against test-id-map
                    testIdMap.entries.find { (_, meta) ->
                        val targetClass = meta["class"] ?: ""
                        val targetMethod = meta["method"] ?: ""
                        val classMatches = fullClass == targetClass || fullClass.endsWith(".$targetClass")
                        val methodMatches =
                            targetMethod.isBlank() || method == targetMethod || method.startsWith("$targetMethod[")
                        classMatches && methodMatches
                    }?.let { (id, _) ->
                        // Check if the testcase block contains a <failure> element
                        val testcaseBlock = content.substringAfter(m.value).substringBefore("</testcase>")
                        val failureMatch = Regex("""<failure[^>]*message=["']([^"']*)["']""").find(testcaseBlock)
                        if (failureMatch != null) {
                            // Sanitize: strip any SHIELD_TEST_SECRET marker values from reason (Principles IX, XI)
                            val raw = failureMatch.groupValues[1]
                            val sanitized = raw.replace(Regex("SHIELD_TEST_SECRET_[A-Z0-9_]+"), "[REDACTED_MARKER]")
                            failedTests[id] = sanitized
                        } else {
                            passedTests.add(id)
                        }
                    }
                }
            }
        }

        // Parse manual_required overrides
        val manualRequired = mutableSetOf<String>()
        if (manualResultsFile.exists()) {
            val content = manualResultsFile.readText()
            Regex("""["']([A-Z]-[0-9]{3})["']""").findAll(content).forEach {
                manualRequired.add(it.groupValues[1])
            }
        }

        val reportsDir = outputDir.get().asFile
        reportsDir.mkdirs()

        // Device metadata written by the FTL workflow next to the JUnit XML.
        var deviceModel: String? = null
        var osVersion: String? = null
        if (xmlDir.exists()) {
            xmlDir.walkTopDown().filter { it.name == "device-metadata.json" }.firstOrNull()?.let { meta ->
                val content = meta.readText()
                deviceModel = Regex(""""device_model"\s*:\s*"([^"]*)"""").find(content)?.groupValues?.get(1)
                osVersion = Regex(""""os_version"\s*:\s*"([^"]*)"""").find(content)?.groupValues?.get(1)
            }
        }

        // Collect screenshot/video evidence (PNG/MP4) downloaded alongside the XML.
        // Files whose name embeds a test ID (e.g. C-001_android_shield_on.png) are
        // attached to that test's report row; the rest go into an appendix.
        val mediaDir = reportsDir.resolve("media")
        mediaDir.mkdirs()
        val mediaById = mutableMapOf<String, MutableList<String>>()
        val unattachedMedia = mutableListOf<String>()
        if (xmlDir.exists()) {
            xmlDir.walkTopDown().filter { it.isFile && it.extension in setOf("png", "mp4") }.forEach { f ->
                val targetName = "${f.parentFile.name}-${f.name}"
                val target = mediaDir.resolve(targetName)
                f.copyTo(target, overwrite = true)
                val idMatch = Regex("""([A-Z]-[0-9]{3})""").find(f.name)?.groupValues?.get(1)
                val meta = idMatch?.let { testIdMap[it] }
                val isIosNamed = f.name.lowercase().contains("ios")
                val attached =
                    if (meta != null && (meta["platform"] == "ios") == isIosNamed) {
                        mediaById.getOrPut(idMatch) { mutableListOf() }.add(targetName)
                        true
                    } else {
                        false
                    }
                if (!attached) unattachedMedia.add(targetName)
            }
        }

        // Build test results array
        val pipelineRunUrl = "https://github.com/$githubRepo/actions/runs/$runId"
        val artifactsAnchor = "$pipelineRunUrl#artifacts"

        fun jsonOrNull(value: String?): String = value?.let { "\"$it\"" } ?: "null"

        val testResults =
            testIdMap.map { (id, meta) ->
                val status =
                    when {
                        id in failedTests -> "failed"
                        id in manualRequired -> "manual_required"
                        id in passedTests -> "passed"
                        else -> "blocked" // device unavailable or test not run
                    }
                val failureReason = failedTests[id]
                // Evidence URL points at the run's Artifacts section, where the media zip lives.
                val isHostJvm = meta["environment"] == "host-jvm"
                val evidenceUrl =
                    if (!isHostJvm && (status == "passed" || status == "failed")) artifactsAnchor else null

                """
                {
                  "test_id": "$id",
                  "test_name": "${meta["test_name"] ?: id}",
                  "commit_sha": "$commitSha",
                  "status": "$status",
                  "platform": "${meta["platform"] ?: "unknown"}",
                  "environment": "${meta["environment"] ?: "unknown"}",
                  "device_model": ${jsonOrNull(if (isHostJvm) null else deviceModel)},
                  "os_version": ${jsonOrNull(if (isHostJvm) null else osVersion)},
                  "evidence_url": ${jsonOrNull(evidenceUrl)},
                  "failure_reason": ${if (failureReason != null) "\"$failureReason\"" else "null"},
                  "timestamp": "$timestamp"
                }
                """.trimIndent()
            }

        val overallStatus =
            when {
                failedTests.isNotEmpty() -> "fail"
                testIdMap.keys.any { it !in passedTests && it !in manualRequired } -> "blocked"
                else -> "pass"
            }

        // Write JSON report
        val jsonReport = reportsDir.resolve("validation-report.json")
        jsonReport.writeText(
            """
            {
              "report_id": "$runId",
              "commit_sha": "$commitSha",
              "trigger_type": "$triggerType",
              "generated_at": "$timestamp",
              "overall_status": "$overallStatus",
              "pipeline_run_url": "$pipelineRunUrl",
              "test_results": [${testResults.joinToString(",")}]
            }
            """.trimIndent(),
        )

        // Write HTML report — embeds screenshot/video evidence inline (media/ is
        // uploaded next to this file, so relative paths resolve inside the artifact zip).
        val htmlReport = reportsDir.resolve("validation-report.html")
        val statusColor =
            if (overallStatus ==
                "pass"
            ) {
                "#2da44e"
            } else if (overallStatus == "fail") {
                "#cf222e"
            } else {
                "#bf8700"
            }

        fun evidenceCell(id: String): String =
            mediaById[id]?.joinToString("") { name ->
                val rel = "media/$name"
                if (name.endsWith(".png")) {
                    """<a href="$rel"><img src="$rel" width="180" alt="$id"></a> """
                } else {
                    """<video src="$rel" width="180" controls muted></video> """
                }
            } ?: ""

        fun appendixMedia(name: String): String =
            if (name.endsWith(".png")) {
                val rel = "media/$name"
                """<figure style="display:inline-block;margin:.5em"><a href="$rel">""" +
                    """<img src="$rel" width="180" alt="$name"></a><figcaption>$name</figcaption></figure>"""
            } else {
                """<p><a href="media/$name">$name</a></p>"""
            }
        htmlReport.writeText(
            """
            <!DOCTYPE html><html><head><meta charset="utf-8">
            <title>ComposeShield Validation Report</title>
            <style>body{font-family:monospace;margin:2em}table{border-collapse:collapse;width:100%}td,th{border:1px solid #ccc;padding:.5em;vertical-align:top}th{background:#f6f8fa}img,video{display:block}</style>
            </head><body>
            <h1>ComposeShield Validation Report</h1>
            <p><b>Overall:</b> <span style="color:$statusColor">$overallStatus</span> | Commit: $commitSha | Run: $runId | Device: ${deviceModel ?: "?"} / ${osVersion ?: "?"} |
               <a href="$artifactsAnchor">Artifacts &amp; videos</a></p>
            <table><tr><th>Test ID</th><th>Name</th><th>Status</th><th>Platform</th><th>Evidence</th><th>Failure Reason</th></tr>
            ${testIdMap.entries.joinToString("") { (id, meta) ->
                val status =
                    when {
                        id in failedTests -> "failed"
                        id in manualRequired -> "manual_required"
                        id in passedTests -> "passed"
                        else -> "blocked"
                    }
                val color =
                    when (status) {
                        "passed" -> "#2da44e"
                        "failed" -> "#cf222e"
                        "manual_required" -> "#bf8700"
                        else -> "#666"
                    }
                val cells =
                    "<td>$id</td><td>${meta["test_name"] ?: id}</td>" +
                        "<td style='color:$color'>$status</td><td>${meta["platform"] ?: ""}</td>" +
                        "<td>${evidenceCell(id)}</td><td>${failedTests[id] ?: ""}</td>"
                "<tr>$cells</tr>"
            }}
            </table>
            <h2>Other captured media</h2>
            ${if (unattachedMedia.isEmpty()) {
                "<p>(none)</p>"
            } else {
                unattachedMedia.joinToString("") {
                    appendixMedia(it)
                }
            }}
            </body></html>
            """.trimIndent(),
        )

        println("✅ Validation report written to ${jsonReport.absolutePath}")
        println("   Overall status: $overallStatus")
        println(
            "   Passed: ${passedTests.size} | Failed: ${failedTests.size} | Manual: ${manualRequired.size} | Blocked: ${testIdMap.size - passedTests.size - failedTests.size - manualRequired.size}",
        )
    }
}
