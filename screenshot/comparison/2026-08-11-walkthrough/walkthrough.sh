#!/usr/bin/env bash
set -euo pipefail
DEVICE="${DEVICE:-emulator-5554}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)"
sleep_sec() { sleep "${1:-1.2}"; }
tap() { adb -s "$DEVICE" shell input tap "$1" "$2"; }
back() { adb -s "$DEVICE" shell input keyevent 4; sleep_sec 0.8; }
launch() { adb -s "$DEVICE" shell am force-stop "$1"; sleep_sec 0.5; adb -s "$DEVICE" shell monkey -p "$1" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep_sec 2; }
shot() { adb -s "$DEVICE" exec-out screencap -p > "$OUT_DIR/$1"; echo "saved $1"; }

walk_current() {
  PKG=io.github.lzyuuu.ailivesaver
  launch "$PKG"
  shot "current-01-home.png"
  tap 205 2100; sleep_sec; shot "current-02-messenger.png"
  tap 540 900; sleep_sec; shot "current-03-messenger-chat.png"
  back; back
  tap 450 2100; sleep_sec; shot "current-04-imaging.png"
  back
  tap 650 2100; sleep_sec; shot "current-05-gallery.png"
  back
  tap 860 2100; sleep_sec; shot "current-06-settings.png"
  back
  launch "$PKG"
  tap 150 1250; sleep_sec; shot "current-07-social-hub-sheet.png"
  tap 150 1200; sleep_sec; shot "current-08-characters.png"
  back; back
  launch "$PKG"
  tap 150 1250; sleep_sec
  tap 410 1200; sleep_sec; shot "current-09-binder.png"
  back; back
  launch "$PKG"
  tap 150 1250; sleep_sec
  tap 150 1350; sleep_sec; shot "current-10-ustagram.png"
  back; back
  launch "$PKG"
  tap 150 1250; sleep_sec
  tap 410 1350; sleep_sec; shot "current-11-rebbit.png"
  back; back
  launch "$PKG"
  tap 150 1250; sleep_sec
  tap 670 1350; sleep_sec; shot "current-12-y.png"
  back; back
  launch "$PKG"
  tap 150 1250; sleep_sec
  tap 920 1350; sleep_sec; shot "current-13-phone.png"
  back; back
  launch "$PKG"
  tap 410 1250; sleep_sec; shot "current-14-creative-suite-sheet.png"
  tap 410 1200; sleep_sec; shot "current-15-aura-swap.png"
  back; back
  launch "$PKG"
  tap 700 1250; sleep_sec; shot "current-16-system-core-sheet.png"
  tap 700 1200; sleep_sec; shot "current-17-storage.png"
  back; back
  launch "$PKG"
  tap 920 1250; sleep_sec; shot "current-18-games-hub.png"
}

walk_reference() {
  PKG=com.mrj.fancyai.github
  adb -s "$DEVICE" shell am start -n "$PKG/com.mrj.fancyai.MainActivity" >/dev/null 2>&1
  sleep_sec 2
  shot "reference-01-home.png"
  tap 180 2100; sleep_sec; shot "reference-02-chat-list.png"
  tap 540 600; sleep_sec; shot "reference-03-chat-detail.png"
  back
  tap 430 2100; sleep_sec; shot "reference-04-characters.png"
  tap 540 500; sleep_sec; shot "reference-05-character-detail.png"
  back; back
  tap 650 2100; sleep_sec; shot "reference-06-gallery.png"
  tap 890 2100; sleep_sec; shot "reference-07-settings.png"
  back; back
  adb -s "$DEVICE" shell am start -n "$PKG/com.mrj.fancyai.MainActivity" >/dev/null 2>&1
  sleep_sec 2
  tap 540 1700; sleep_sec; shot "reference-08-root-cta.png"
}

case "${1:-all}" in
  current) walk_current ;;
  reference) walk_reference ;;
  all) walk_current; walk_reference ;;
  *) echo "usage: $0 [current|reference|all]" >&2; exit 1 ;;
esac
