#!/bin/bash
set -e

# Setup SDK paths
export ANDROID_SDK_ROOT="/Users/iamkeeler/Library/Android/sdk"
export PATH="$ANDROID_SDK_ROOT/emulator:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo "=== Wiping and Reinstalling on Emulator ==="

# 1. Detect AVD name of the currently running emulator
echo "Detecting running AVD..."
# Get AVD name and strip the trailing "OK" status line and carriage returns
AVD_NAME=$(adb emu avd name 2>/dev/null | grep -v "OK" | tr -d '\r' | xargs || true)
if [ -z "$AVD_NAME" ]; then
    echo "No running AVD detected via adb emu. Defaulting to Medium_Phone_API_36."
    AVD_NAME="Medium_Phone_API_36"
else
    echo "Detected running AVD: $AVD_NAME"
fi

# 2. Kill the emulator
echo "Shutting down the emulator..."
adb emu kill || true
sleep 3

# Wait until adb devices does not show the emulator
while adb devices | grep -q "emulator"; do
    echo "Waiting for emulator to shut down..."
    sleep 2
done
echo "Emulator is shut down."

# 3. Start the emulator with -wipe-data
echo "Starting emulator $AVD_NAME with -wipe-data..."
emulator -avd "$AVD_NAME" -wipe-data -no-snapshot-load > /dev/null 2>&1 &

# 4. Wait for boot completion
echo "Waiting for emulator to boot (this may take a minute)..."
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
    sleep 3
done
echo "Emulator is booted!"

# 5. Clean build and install
echo "Performing clean build and install..."
./gradlew clean installDebug

echo "=== Complete! App has been clean installed on a wiped emulator. ==="
