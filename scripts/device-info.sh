#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_SERIAL:?Set ANDROID_SERIAL to the device you intend to inspect}"
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.mi.os.version.name
adb shell getprop ro.miui.ui.version.name
adb shell getprop ro.soc.model
adb shell getprop ro.product.cpu.abilist
adb shell getconf PAGE_SIZE
adb shell cat /proc/meminfo
adb shell dumpsys thermalservice
adb shell dumpsys battery
