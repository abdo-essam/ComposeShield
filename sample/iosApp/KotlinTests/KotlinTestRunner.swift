import XCTest

/// Runs the Kotlin/Native test executable built by Gradle's `linkDebugTestIosArm64`
/// task and reports pass/fail back to Xcode's test runner.
///
/// The binary is embedded inside the test bundle's Resources folder during the
/// "Embed Kotlin Test Binary" build phase so it is code-signed and deployable
/// to a real iPhone without developer-mode restrictions.
final class KotlinTestRunner: XCTestCase {

    func testKotlinSuite() throws {
        // Locate the embedded test binary inside the bundle.
        guard let binaryURL = Bundle(for: KotlinTestRunner.self)
            .url(forResource: "test", withExtension: "kexe") else {
            XCTFail("test.kexe not found in test bundle — make sure the " +
                    "'Embed Kotlin Test Binary' build phase ran successfully.")
            return
        }

        // The binary must be executable; on-device it is signed by Xcode.
        let fm = FileManager.default
        var isDir: ObjCBool = false
        XCTAssertTrue(fm.fileExists(atPath: binaryURL.path, isDirectory: &isDir),
                      "test.kexe missing at \(binaryURL.path)")

        // Ensure it is executable (should already be from the build phase chmod).
        try fm.setAttributes([.posixPermissions: 0o755], ofItemAtPath: binaryURL.path)

        // Launch the Kotlin test binary as a subprocess.
        let process = Process()
        process.executableURL = binaryURL
        // Pass --no-exit so the runner stays alive long enough for XCTest to capture output.
        // Kotlin/Native's built-in test runner honours --ktest_logger=GTEST for CI-style output.
        process.arguments = ["--ktest_logger=TEAMCITY"]

        let outputPipe = Pipe()
        let errorPipe  = Pipe()
        process.standardOutput = outputPipe
        process.standardError  = errorPipe

        try process.run()
        process.waitUntilExit()

        // Capture and print output so it surfaces in Xcode's test log.
        let outputData = outputPipe.fileHandleForReading.readDataToEndOfFile()
        let errorData  = errorPipe.fileHandleForReading.readDataToEndOfFile()
        if let out = String(data: outputData, encoding: .utf8), !out.isEmpty {
            print("=== Kotlin test output ===\n\(out)")
        }
        if let err = String(data: errorData, encoding: .utf8), !err.isEmpty {
            print("=== Kotlin test stderr ===\n\(err)")
        }

        XCTAssertEqual(process.terminationStatus, 0,
                       "Kotlin test binary exited with status \(process.terminationStatus) — " +
                       "see test output above for failing tests.")
    }
}
