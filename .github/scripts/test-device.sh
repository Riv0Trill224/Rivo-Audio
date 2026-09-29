#!/usr/bin/env bash
set +e
gradle :app:connectedDebugAndroidTest --stacktrace
result=$?
adb logcat -d > device-logcat.txt
mkdir -p screenshots
for name in player lyrics settings; do
  adb pull "/sdcard/Download/rivo-$name.png" screenshots/
done


adb pull /sdcard/Download/rivo-video.png screenshots/rivo-video.png || true
adb pull /sdcard/Download/rivo-video-failure.png screenshots/rivo-video-failure.png || true
adb pull /sdcard/Download/rivo-video-failure.xml screenshots/rivo-video-failure.xml || true
adb pull /sdcard/Download/rivo-video-stage.png screenshots/rivo-video-stage.png || true
adb pull /sdcard/Download/rivo-video-stage.xml screenshots/rivo-video-stage.xml || true
exit "$result"
