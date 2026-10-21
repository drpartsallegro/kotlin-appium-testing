#!/usr/bin/env bash
# Run on the already-booted emulator, before ./gradlew test.
# AndroidDriver session creation launches Settings. SettingsPage clicks the ANR
# Wait button only after that session exists, so the first newSession is rejected
# when Package Manager has no path yet or System UI is showing "isn't responding".
set -euo pipefail

SETTINGS_COMPONENT="com.android.settings/.Settings"
POLL_SECONDS=120
ANR_ATTEMPTS=5
UI_DUMP_DEVICE="/data/local/tmp/uidump.xml"
UI_DUMP_HOST="/tmp/uidump.xml"
WINDOW_HITS_HOST="/tmp/window-anr.txt"

package_path_ready() {
  case "$1" in
    *package:/*) return 0 ;;
    *) return 1 ;;
  esac
}

wait_for_package_manager() {
  local deadline=$((SECONDS + POLL_SECONDS))
  local i=0
  local path
  while true; do
    i=$((i + 1))
    path=$(adb shell pm path com.android.settings 2>&1 || true)
    path=$(printf '%s' "$path" | tr -d '\r')
    printf 'pm path poll %s: %s\n' "$i" "${path:-<empty>}"
    if package_path_ready "$path"; then
      printf '%s\n' "Package manager returned a path for com.android.settings"
      return 0
    fi
    if (( SECONDS >= deadline )); then
      printf '%s\n' "Package manager did not print a path for com.android.settings within ${POLL_SECONDS}s"
      return 1
    fi
    sleep 2
  done
}

animation_stopped() {
  local anim=$1
  local activity=$2
  local booted=$3
  [ "$booted" = "1" ] || return 1
  if printf '%s\n' "$activity" | grep -q 'mBootAnimationComplete=false'; then
    return 1
  fi
  if [ "$anim" = "running" ]; then
    return 1
  fi
  if [ "$anim" = "stopped" ]; then
    return 0
  fi
  if printf '%s\n' "$activity" | grep -q 'mBootAnimationComplete=true'; then
    return 0
  fi
  if printf '%s\n' "$activity" | grep -q 'mBooted=true'; then
    return 0
  fi
  return 1
}

wait_for_boot_animation() {
  local deadline=$((SECONDS + POLL_SECONDS))
  local i=0
  local anim activity booted
  while true; do
    i=$((i + 1))
    booted=$(adb shell getprop sys.boot_completed 2>&1 || true)
    booted=$(printf '%s' "$booted" | tr -d '\r\n')
    anim=$(adb shell getprop init.svc.bootanim 2>&1 || true)
    anim=$(printf '%s' "$anim" | tr -d '\r\n')
    activity=$(timeout 30 adb shell dumpsys activity 2>/dev/null | tr -d '\r' | grep -E 'mBootAnimationComplete=|mBooted=' || true)
    printf 'bootanim poll %s: sys.boot_completed=%s init.svc.bootanim=%s\n' \
      "$i" "${booted:-<empty>}" "${anim:-<empty>}"
    if [ -n "$activity" ]; then
      printf '%s\n' "$activity"
    else
      printf '%s\n' "dumpsys activity: no mBootAnimationComplete/mBooted line"
    fi
    if animation_stopped "$anim" "$activity" "$booted"; then
      printf '%s\n' "Boot animation has stopped"
      return 0
    fi
    if (( SECONDS >= deadline )); then
      printf '%s\n' "Boot animation did not stop within ${POLL_SECONDS}s (init.svc.bootanim / dumpsys activity)"
      return 1
    fi
    sleep 2
  done
}

dump_nodes() {
  # uiautomator dump is one line. tr cannot insert a newline after '>' (extra
  # characters in the replacement set are ignored), so split with sed.
  sed 's/>/\n/g' "$UI_DUMP_HOST"
}

anr_visible() {
  if [ -s "$UI_DUMP_HOST" ] && grep -i -q "isn't responding" "$UI_DUMP_HOST"; then
    return 0
  fi
  if [ -s "$UI_DUMP_HOST" ] && grep -F -q 'android:id/aerr_wait' "$UI_DUMP_HOST"; then
    return 0
  fi
  if [ -s "$WINDOW_HITS_HOST" ] && grep -i -q "isn't responding" "$WINDOW_HITS_HOST"; then
    return 0
  fi
  if [ -s "$WINDOW_HITS_HOST" ] && grep -F -q 'Application Not Responding' "$WINDOW_HITS_HOST"; then
    return 0
  fi
  return 1
}

# Prints "x y" for the Wait button. Prints nothing when the only button is Close app.
wait_tap_point() {
  local node bounds close_node close_bounds
  local x1 y1 x2 y2 x y cx1 cy1 cx2 cy2
  node=$(dump_nodes | grep -F 'android:id/aerr_wait' | grep -v -F 'aerr_close' | head -n 1 || true)
  if [ -z "$node" ]; then
    node=$(dump_nodes | grep -F 'text="Wait"' | grep -v -F 'aerr_close' | grep -v -F 'Close app' | head -n 1 || true)
  fi
  case "$node" in
    *aerr_close* | *'Close app'*)
      printf '%s\n' "Refusing to tap Close app" >&2
      return 0
      ;;
  esac
  [ -n "$node" ] || return 0
  bounds=$(printf '%s\n' "$node" | sed -n 's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')
  [ -n "$bounds" ] || return 0
  # shellcheck disable=SC2086
  set -- $bounds
  x1=$1
  y1=$2
  x2=$3
  y2=$4
  x=$(((10#$x1 + 10#$x2) / 2))
  y=$(((10#$y1 + 10#$y2) / 2))
  close_node=$(dump_nodes | grep -F 'android:id/aerr_close' | head -n 1 || true)
  if [ -n "$close_node" ]; then
    close_bounds=$(printf '%s\n' "$close_node" | sed -n 's/.*bounds="\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]\[\([0-9][0-9]*\),\([0-9][0-9]*\)\]".*/\1 \2 \3 \4/p')
    if [ -n "$close_bounds" ]; then
      # shellcheck disable=SC2086
      set -- $close_bounds
      cx1=$1
      cy1=$2
      cx2=$3
      cy2=$4
      if [ "$x" -ge "$cx1" ] && [ "$x" -le "$cx2" ] && [ "$y" -ge "$cy1" ] && [ "$y" -le "$cy2" ]; then
        printf '%s\n' "Refusing to tap Close app bounds" >&2
        return 0
      fi
    fi
  fi
  printf '%s %s\n' "$x" "$y"
}

dismiss_system_ui_anr() {
  local attempt point x y
  for attempt in $(seq 1 "$ANR_ATTEMPTS"); do
    adb shell rm -f "$UI_DUMP_DEVICE" >/dev/null 2>&1 || true
    printf 'uiautomator dump attempt %s\n' "$attempt"
    timeout 30 adb shell uiautomator dump "$UI_DUMP_DEVICE" > /tmp/ui-dump-status.txt 2>&1 || true
    cat /tmp/ui-dump-status.txt || true
    : > "$UI_DUMP_HOST"
    adb shell cat "$UI_DUMP_DEVICE" 2>/dev/null | tr -d '\r' > "$UI_DUMP_HOST" || true
    : > "$WINDOW_HITS_HOST"
    adb shell dumpsys window 2>/dev/null | tr -d '\r' | grep -i -e "isn't responding" -e "Application Not Responding" > "$WINDOW_HITS_HOST" || true
    if [ -s "$WINDOW_HITS_HOST" ]; then
      printf '%s\n' "dumpsys window ANR lines:"
      cat "$WINDOW_HITS_HOST"
    else
      printf '%s\n' "dumpsys window: no isn't responding / Application Not Responding line"
    fi
    if ! anr_visible; then
      printf '%s\n' "ANR text is gone"
      return 0
    fi
    printf '%s\n' "System UI ANR is showing; looking for the Wait button"
    point=$(wait_tap_point)
    if [[ "$point" =~ ^[0-9]+\ [0-9]+$ ]]; then
      read -r x y <<<"$point"
      printf 'Tapping Wait at %s,%s\n' "$x" "$y"
      adb shell input tap "$x" "$y"
    else
      printf '%s\n' "ANR is still visible but Wait bounds were not in the dump; not tapping"
    fi
    if [ "$attempt" -lt "$ANR_ATTEMPTS" ]; then
      sleep 2
    fi
  done
  printf '%s\n' "System UI ANR text was still present after dismiss attempts"
  return 1
}

settings_launch_ok() {
  printf '%s\n' "$1" | tr -d '\r' | grep -E -q '^Status: ok[[:space:]]*$'
}

require_settings_launch() {
  local am_out am_code=0
  printf 'am start -W -n %s\n' "$SETTINGS_COMPONENT"
  am_out=$(adb shell am start -W -n "$SETTINGS_COMPONENT" 2>&1) || am_code=$?
  printf '%s\n' "$am_out"
  if ! settings_launch_ok "$am_out"; then
    printf 'am start did not complete with Status: ok (exit %s)\n' "$am_code"
    return 1
  fi
  if [ "$am_code" -ne 0 ]; then
    printf 'am start exited %s\n' "$am_code"
    return "$am_code"
  fi
  printf '%s\n' "Settings launch completed with Status: ok"
}

main() {
  wait_for_package_manager
  wait_for_boot_animation
  dismiss_system_ui_anr
  require_settings_launch
  dismiss_system_ui_anr
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main
fi
