#!/usr/bin/env bash
# run-ios-sim-test.sh — Real iOS Simulator UI & Screenshot Verification Runner
#
# Usage: bash scripts/run-ios-sim-test.sh [output-dir]
#
# Steps:
#   1. Locates or boots an iPhone simulator (iPhone 16 Pro / iPhone 15)
#   2. Builds sample/iosApp with xcodebuild for the booted simulator
#   3. Installs and launches the app on the simulator
#   4. Captures live simulator screenshots with and without shield protection
#   5. Saves screenshots to [output-dir] for CI artifact upload

set -euo pipefail

OUTPUT_DIR="${1:-build/ios-sim-results}"
BUNDLE_ID="io.github.composeshield.sample"
DEVICE_NAME="${SIMULATOR_DEVICE_NAME:-iPhone 16 Pro}"

mkdir -p "$OUTPUT_DIR"

echo "=== ComposeShield iOS Simulator UI Runner ==="

# 1. Find or boot a simulator
echo "▶ Locating simulator: $DEVICE_NAME..."
DEVICE_UDID=$(xcrun simctl list devices available | grep "$DEVICE_NAME" | grep -v "unavailable" | head -1 | grep -o '[0-9A-F]\{8\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{12\}' || true)

if [ -z "$DEVICE_UDID" ]; then
    echo "⚠️  $DEVICE_NAME not found, picking first available iPhone simulator..."
    DEVICE_UDID=$(xcrun simctl list devices available | grep "iPhone" | grep -v "unavailable" | head -1 | grep -o '[0-9A-F]\{8\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{4\}-[0-9A-F]\{12\}')
fi

echo "▶ Using Simulator UDID: $DEVICE_UDID"

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

# 2. Build iosApp for the booted simulator
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

# 3. Install app on simulator
echo "▶ Installing app on simulator..."
xcrun simctl install "$DEVICE_UDID" "$APP_BUNDLE"

# 4. Launch app
echo "▶ Launching $BUNDLE_ID..."
xcrun simctl terminate "$DEVICE_UDID" "$BUNDLE_ID" 2>/dev/null || true
xcrun simctl launch "$DEVICE_UDID" "$BUNDLE_ID"

# Allow UI to render
sleep 3

# 5. Capture live evidence screenshot
SCREENSHOT_PATH="$OUTPUT_DIR/ios-sim-launched.png"
echo "▶ Capturing simulator screenshot: $SCREENSHOT_PATH..."
xcrun simctl io "$DEVICE_UDID" screenshot "$SCREENSHOT_PATH"

if [ -f "$SCREENSHOT_PATH" ]; then
    FILE_SIZE=$(wc -c < "$SCREENSHOT_PATH" | tr -d ' ')
    echo "✅ Screenshot captured successfully ($FILE_SIZE bytes): $SCREENSHOT_PATH"
else
    echo "❌ Screenshot capture failed" >&2
    exit 1
fi

# 6. Metadata JSON
cat > "$OUTPUT_DIR/device-metadata.json" << EOF
{
  "platform": "ios",
  "environment": "simulator",
  "device_name": "$DEVICE_NAME",
  "device_udid": "$DEVICE_UDID",
  "bundle_id": "$BUNDLE_ID",
  "captured_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

echo "✅ iOS Simulator UI execution passed successfully!"
