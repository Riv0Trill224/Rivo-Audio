#!/usr/bin/env bash
set +e
gradle :app:connectedDebugAndroidTest --stacktrace
result=$?
adb logcat -d > device-logcat.txt
mkdir -p screenshots
for name in player lyrics settings; do
  adb pull "/sdcard/Download/rivo-$name.png" screenshots/
done
exit "$result"
