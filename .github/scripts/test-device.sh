#!/usr/bin/env bash
set +e
gradle :app:connectedDebugAndroidTest --stacktrace
result=$?
adb logcat -d > device-logcat.txt
adb pull /sdcard/Android/data/com.riv0trill.rivoaudio/files/ screenshots
exit "$result"
