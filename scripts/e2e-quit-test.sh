#!/usr/bin/env bash
# Emulator test for Settings > Quit Fastwise, run by GitHub Actions on the built APK.
#
# 1. Starts a fast and checks the timer service, its notification and the goal alarms are on.
# 2. Quits from Settings and checks the process, service, notifications and alarms are gone.
# 3. Waits, then checks nothing came back on its own.
# 4. Reinstalls the APK (Android sends "package replaced", like after an update) and checks the
#    timer still stays off.
# 5. Opens the app and checks the running fast and its timer are back.
# 6. Reinstalls again and checks the timer does come back now, which proves step 4 tested something.
#
# Usage: scripts/e2e-quit-test.sh <folder with the APK>
# Screenshots, UI dumps and system state go to e2e-output/ for the uploaded test report.

set -uo pipefail

APK=$(ls "$1"/*.apk | head -n 1)
PKG=app.fastwise
HERE=$(cd "$(dirname "$0")" && pwd)
OUT=e2e-output
mkdir -p "$OUT"

step() { echo; echo "== $*"; }
pass() { echo "   ok: $*"; }

snapshot() {
  local tag=$1
  adb exec-out screencap -p > "$OUT/$tag.png" 2>/dev/null || true
  {
    echo "--- pid"; adb shell pidof "$PKG"
    echo "--- services"; adb shell dumpsys activity services "$PKG"
    echo "--- alarms"; adb shell dumpsys alarm | grep -E "Alarm\{.* $PKG\}|$PKG\.action"
    echo "--- notifications"; adb shell dumpsys notification | grep -E "NotificationRecord\(.*pkg=$PKG"
    echo "--- focused window"; adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp"
  } > "$OUT/$tag.txt" 2>&1 || true
}

fail() {
  echo "FAIL: $*"
  snapshot failure
  exit 1
}

# ---- What's running ----------------------------------------------------------------------------

process_alive() { [ -n "$(adb shell pidof "$PKG" | tr -d '\r')" ]; }
process_gone() { ! process_alive; }
service_running() { adb shell dumpsys activity services "$PKG" | grep -q "FastingService"; }
alarm_count() { adb shell dumpsys alarm | grep -cE "Alarm\{[0-9a-f]+ type .* $PKG\}" || true; }
notification_count() { adb shell dumpsys notification | grep -cE "NotificationRecord\(.*pkg=$PKG" || true; }
has_alarms() { [ "$(alarm_count)" -ge 1 ]; }
has_notification() { [ "$(notification_count)" -ge 1 ]; }

# wait_for <seconds> <check>: retries the check once a second.
wait_for() {
  local secs=$1 i
  shift
  for ((i = 0; i < secs; i++)); do
    "$@" && return 0
    sleep 1
  done
  return 1
}

expect_all_on() {
  wait_for 15 service_running || fail "$1: timer service isn't running"
  wait_for 10 has_notification || fail "$1: no timer notification"
  wait_for 10 has_alarms || fail "$1: no goal alarms scheduled"
  pass "$1: timer service running, $(notification_count) notification(s), $(alarm_count) alarm(s)"
}

expect_all_off() {
  service_running && fail "$1: timer service is running"
  [ "$(notification_count)" -eq 0 ] || fail "$1: $(notification_count) Fastwise notification(s) still showing"
  [ "$(alarm_count)" -eq 0 ] || fail "$1: $(alarm_count) Fastwise alarm(s) still scheduled"
  pass "$1: no timer service, notifications or alarms"
}

# ---- Screen helpers ----------------------------------------------------------------------------

read -r W H < <(adb shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)

close_system_dialogs() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
}

# Prints "x y" for the center of the on-screen node with exactly this text. Reading the screen can
# fail while the emulator is busy, and Android's "System UI isn't responding" dialog can cover the
# app, so it retries, choosing Wait on that dialog and closing other system dialogs in the way.
find_on_screen() {
  local try ax ay
  for try in 1 2 3 4 5 6; do
    adb shell rm -f /sdcard/ui.xml > /dev/null 2>&1
    adb shell uiautomator dump /sdcard/ui.xml >> "$OUT/uiautomator.log" 2>&1
    if adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1; then
      if grep -q "responding" "$OUT/ui.xml" && read -r ax ay < <(python3 "$HERE/ui_find.py" "$OUT/ui.xml" "Wait"); then
        echo "   (Android showed an \"isn't responding\" dialog, choosing Wait)"
        tap "$ax" "$ay"
        sleep 3
        continue
      fi
      python3 "$HERE/ui_find.py" "$OUT/ui.xml" "$1"
      return
    fi
    close_system_dialogs
    sleep 3
  done
  return 1
}

# Scrolls down until the text is on screen, then prints its center.
find_scrolling() {
  local i
  for i in 1 2 3 4 5 6 7 8; do
    find_on_screen "$1" && return 0
    adb shell input swipe $((W / 2)) $((H * 7 / 10)) $((W / 2)) $((H * 3 / 10)) 400
    sleep 1
  done
  return 1
}

tap() { adb shell input tap "$1" "$2"; }

# ---- Test --------------------------------------------------------------------------------------

# A freshly booted CI emulator is slow, and Android's own System UI can stop responding for a while.
# Hide the "isn't responding" dialogs so they can't cover the app, and give the system time to settle.
step "Let the emulator settle"
adb shell settings put global hide_error_dialogs 1 || true
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
sleep 30
close_system_dialogs

step "Install and open Fastwise ($APK, screen ${W}x${H})"
adb install -r "$APK" || fail "couldn't install the APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am start -W -n "$PKG/.ui.MainActivity" || fail "couldn't open the app"
sleep 3
snapshot opened

# While fasting, the Timer screen changes every second and uiautomator never sees it idle,
# so find the Settings tab before starting the fast.
read -r SX SY < <(find_on_screen "Settings") || fail "Settings tab not found"
read -r FX FY < <(find_scrolling "Start fast without logging a meal") || fail "Start fast button not found"

step "Start a fast"
tap "$FX" "$FY"
process_alive || fail "app process isn't running"
expect_all_on "fasting"
snapshot fasting

step "Settings > Quit Fastwise > Quit"
tap "$SX" "$SY"
sleep 2
read -r QX QY < <(find_scrolling "Quit Fastwise") || fail "Quit Fastwise row not found in Settings"
tap "$QX" "$QY"
sleep 1
snapshot quit-dialog
read -r CX CY < <(find_on_screen "Quit") || fail "Quit button not found in the dialog"
find_on_screen "End fast and quit" > /dev/null || fail "End fast and quit button not found in the dialog"
tap "$CX" "$CY"

step "Right after quitting"
wait_for 15 process_gone || fail "app process still running 15 seconds after Quit"
pass "app process ended"
expect_all_off "after Quit"
snapshot after-quit

step "30 seconds later, nothing should have come back"
sleep 30
process_gone || fail "app process came back on its own"
expect_all_off "30 seconds after Quit"

step "Reinstall the APK (package replaced, like an app update)"
adb install -r "$APK" || fail "couldn't reinstall the APK"
sleep 15
expect_all_off "after an app update while quit"
snapshot after-update

step "Open the app again"
adb shell am start -W -n "$PKG/.ui.MainActivity" || fail "couldn't open the app"
expect_all_on "reopened, fast still running"
snapshot reopened

step "Control: reinstall again while not quit, the timer should come back by itself"
adb install -r "$APK" || fail "couldn't reinstall the APK"
wait_for 20 has_notification || fail "timer didn't come back after an app update when not quit"
wait_for 10 has_alarms || fail "alarms didn't come back after an app update when not quit"
pass "timer notification and alarms came back on their own"

echo
echo "PASS: Quit stops Fastwise completely, it stays stopped, and opening the app brings the fast back."
