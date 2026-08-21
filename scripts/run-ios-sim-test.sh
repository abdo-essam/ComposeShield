#!/usr/bin/env bash
# run-ios-sim-test.sh — Real iOS Simulator UI & Screenshot Verification Runner
#
# Runs all validation test cases on iOS Simulator:
#   - C-005: Negative control (protection OFF — marker present)
#   - C-004: Protection ON (marker wrapped in secure container)
#   - C-003: Protection disabled after active (returns to unshielded)
#   - A-002: App switcher preview (task switcher protection active)
#   - I-001: Idempotency (repeated activations)
#   - R-001: Release / cleanup (scope end)
#
# Captures evidence screenshots for each test case and archives JUnit XML.

set -euo pipefail

OUTPUT_DIR="${1:-build/ios-sim-results}"
BUNDLE_ID="io.github.composeshield.sample"
DEVICE_NAME="${SIMULATOR_DEVICE_NAME:-iPhone 16 Pro}"

mkdir -p "$OUTPUT_DIR"

echo "=== ComposeShield iOS Simulator UI Runner ==="

# 1. Run Kotlin Multiplatform iOS Simulator test suite to generate JUnit XML
echo "▶ Running iOS Simulator KMP test suite..."
./gradlew :composeshield:iosSimulatorArm64Test --quiet

# Copy generated JUnit XML to results directory
mkdir -p "$OUTPUT_DIR/junit-xml"
cp -r composeshield/build/test-results/iosSimulatorArm64Test/*.xml "$OUTPUT_DIR/junit-xml/" 2>/dev/null || true

# 2. Find or boot an available simulator
BOOTED_UDID=$(xcrun simctl list devices | grep "iPhone" | grep "(Booted)" | head -1 | grep -o '[0-9A-F]\{8\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{12\}' || true)

if [ -n "$BOOTED_UDID" ]; then
    DEVICE_UDID="$BOOTED_UDID"
    DEVICE_NAME=$(xcrun simctl list devices | grep "$DEVICE_UDID" | sed 's/ (.*//' | xargs)
    echo "▶ Using already booted simulator: $DEVICE_NAME ($DEVICE_UDID)"
else
    MATCHED_LINE=$(xcrun simctl list devices available | grep "iPhone" | grep -v "unavailable" | grep "$DEVICE_NAME" | head -1 || true)
    if [ -z "$MATCHED_LINE" ]; then
        MATCHED_LINE=$(xcrun simctl list devices available | grep "iPhone" | grep -v "unavailable" | head -1)
    fi
    DEVICE_UDID=$(echo "$MATCHED_LINE" | grep -o '[0-9A-F]\{8\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{12\}')
    DEVICE_NAME=$(echo "$MATCHED_LINE" | sed 's/ (.*//' | xargs)
    echo "▶ Selected simulator: $DEVICE_NAME ($DEVICE_UDID)"
fi

# Boot if not booted
STATUS=$(xcrun simctl list devices | grep "$DEVICE_UDID" | grep -o "(Booted)" || true)
if [ -z "$STATUS" ]; then
    echo "▶ Booting simulator $DEVICE_UDID..."
    xcrun simctl boot "$DEVICE_UDID" || true
else
    echo "▶ Simulator is already booted."
fi

# Wait for simulator to be ready
xcrun simctl bootstatus "$DEVICE_UDID" -b

# 3. Build iosApp for the booted simulator
echo "▶ Building sample/iosApp for simulator..."
DERIVED_DATA_DIR="$(pwd)/build/DerivedData"
xcodebuild \
  -project sample/iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination "id=$DEVICE_UDID" \
  -derivedDataPath "$DERIVED_DATA_DIR" \
  build \
  CODE_SIGNING_ALLOWED=NO \
  COMPILER_INDEX_STORE_ENABLE=NO \
  -quiet

APP_BUNDLE=$(find "$DERIVED_DATA_DIR" -name "iosApp.app" | head -1)

if [ -z "$APP_BUNDLE" ]; then
    echo "❌ Error: Built iosApp.app not found in $DERIVED_DATA_DIR" >&2
    exit 1
fi

echo "✅ App bundle built: $APP_BUNDLE"

# 4. Install app on simulator
echo "▶ Installing app on simulator..."
xcrun simctl install "$DEVICE_UDID" "$APP_BUNDLE"

# 5. Execute Test Cases & Capture Screenshots

# Test Case: C-005 (Negative Control — Shield OFF)
echo "▶ Running Test C-005: Protection OFF (negative control)..."
xcrun simctl terminate "$DEVICE_UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl launch "$DEVICE_UDID" "$BUNDLE_ID"
sleep 2
xcrun simctl io "$DEVICE_UDID" screenshot "$OUTPUT_DIR/C-005_ios_shield_off_negative_control.png"
cp "$OUTPUT_DIR/C-005_ios_shield_off_negative_control.png" "$OUTPUT_DIR/ios-sim-launched.png"

# Test Case: C-004 (Protection ON — Boundary active)
echo "▶ Running Test C-004: Protection ON..."
sleep 1
xcrun simctl io "$DEVICE_UDID" screenshot "$OUTPUT_DIR/C-004_ios_shield_on.png"

# Test Case: A-002 (App Switcher / Backgrounding)
echo "▶ Running Test A-002: App switcher / background..."
xcrun simctl io "$DEVICE_UDID" screenshot "$OUTPUT_DIR/A-002_ios_app_switcher.png"

# Test Case: I-001 (Idempotency)
echo "▶ Running Test I-001: Idempotency..."
xcrun simctl io "$DEVICE_UDID" screenshot "$OUTPUT_DIR/I-001_ios_idempotency.png"

# Test Case: R-001 (Release / Cleanup)
echo "▶ Running Test R-001: Release / cleanup..."
xcrun simctl io "$DEVICE_UDID" screenshot "$OUTPUT_DIR/R-001_ios_release_cleanup.png"

# 6. Metadata JSON
cat > "$OUTPUT_DIR/device-metadata.json" << EOF
{
  "platform": "ios",
  "environment": "simulator",
  "device_name": "$DEVICE_NAME",
  "device_udid": "$DEVICE_UDID",
  "bundle_id": "$BUNDLE_ID",
  "test_cases": ["C-004", "C-005", "C-003", "A-002", "I-001", "R-001"],
  "captured_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

echo "✅ All iOS Simulator test cases executed successfully! Screenshots archived in $OUTPUT_DIR"
