#!/bin/bash
# Builds the TonenAI Android app and installs it on the phone connected with USB debugging.
# Double-click this file in Finder, or run it from a terminal. Everything it prints also goes to build-log.txt.
cd "$(dirname "$0")" || exit 1
exec > >(tee build-log.txt) 2>&1

SDK=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)
ADB="$SDK/platform-tools/adb"
if ! /usr/libexec/java_home >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    for jbr in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"; do
        [ -x "$jbr/bin/java" ] && export JAVA_HOME="$jbr" && break
    done
fi

echo "== Build ($(date))"
./gradlew :app:testDebugUnitTest :app:assembleDebug || { echo "== BUILD FAILED"; exit 1; }
APK=app/build/outputs/apk/debug/app-debug.apk
echo "== Built $APK"

[ -x "$ADB" ] || { echo "== adb not found at $ADB: copy the APK to the phone and open it there"; exit 1; }
echo "== Phones seen by adb:"
"$ADB" devices -l
if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "== NO PHONE (or more than one). Plug it in with USB debugging on, accept the prompt on the phone, and run this again."
    exit 1
fi
"$ADB" install -r "$APK" || { echo "== INSTALL FAILED"; exit 1; }
"$ADB" shell am start -n com.tonenai.app/.MainActivity
echo "== INSTALLED. Unplug the phone and plug the amp into it."
