#!/usr/bin/env bash
set -euo pipefail
DEVICE=emulator-5554
OUT="$(cd "$(dirname "$0")" && pwd)"
ACT=io.github.lzyuuu.ailivesaver/.MainActivity
REF=com.mrj.fancyai.github/com.mrj.fancyai.MainActivity
shot() { adb -s "$DEVICE" exec-out screencap -p > "$OUT/$1"; echo "saved $1"; }
launch_current() { adb -s "$DEVICE" shell am start -n "$ACT" >/dev/null; sleep 2; }
open_app() { adb -s "$DEVICE" shell am start -n "$ACT" -e open_desktop_app "$1" >/dev/null; sleep 2; }
tap() { adb -s "$DEVICE" shell input tap "$1" "$2"; sleep 1.2; }
back() { adb -s "$DEVICE" shell input keyevent 4; sleep 1; }
launch_current; shot current-v2-01-home.png
open_app messenger; shot current-v2-02-messenger.png; tap 540 420; sleep 1.5; shot current-v2-03-messenger-chat.png; back; back
open_app imaging; shot current-v2-04-imaging.png; back
open_app gallery; shot current-v2-05-gallery.png; back
open_app settings; shot current-v2-06-settings.png; back
launch_current; tap 154 1260; shot current-v2-07-social-hub.png; back
open_app characters; shot current-v2-08-characters.png; back
open_app binder; shot current-v2-09-binder.png; back
open_app ustagram; shot current-v2-10-ustagram.png; back
open_app rebbit; shot current-v2-11-rebbit.png; back
open_app y; shot current-v2-12-y.png; back
open_app phone; shot current-v2-13-phone.png; back
launch_current; tap 411 1260; shot current-v2-14-creative-suite.png; back
open_app aura_swap; shot current-v2-15-aura-swap.png; back
launch_current; tap 668 1260; shot current-v2-16-system-core.png; back
open_app storage; shot current-v2-17-storage.png; back
launch_current; tap 925 1260; shot current-v2-18-games-hub.png
adb -s "$DEVICE" shell am start -n "$REF" >/dev/null; sleep 2; shot reference-v2-01-home.png
tap 147 2158; sleep 1.5; shot reference-v2-02-chat-list.png
tap 540 500; sleep 1.5; shot reference-v2-03-chat-detail.png
tap 407 2158; sleep 1.5; shot reference-v2-04-characters.png
tap 540 500; sleep 1.5; shot reference-v2-05-character-detail.png
tap 615 2158; sleep 1.5; shot reference-v2-06-gallery.png
tap 871 2158; sleep 1.5; shot reference-v2-07-settings.png
tap 540 1700; sleep 1; shot reference-v2-08-settings-scrolled.png
