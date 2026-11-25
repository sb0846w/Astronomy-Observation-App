#!/bin/bash
# Builds the AstroLog debug APK on a Mac that has Android Studio installed.
# Usage:  cd ~/Desktop/AstroLog && bash build-android-mac.sh
set -e
cd "$(dirname "$0")/AndroidApp"

# Use Android Studio's bundled Java 17+ if JAVA_HOME isn't set
if [ -z "$JAVA_HOME" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
# Default Android SDK location on macOS
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -d "$ANDROID_HOME" ]; then
  echo "Android SDK not found at $ANDROID_HOME. Open Android Studio once (it installs the SDK), then re-run."
  exit 1
fi
echo "sdk.dir=$ANDROID_HOME" > local.properties

chmod +x gradlew
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk ../AstroLog-debug.apk
echo ""
echo "✅ APK built: $(cd .. && pwd)/AstroLog-debug.apk"
echo "   Install on a connected phone/emulator with:  $ANDROID_HOME/platform-tools/adb install -r ../AstroLog-debug.apk"
