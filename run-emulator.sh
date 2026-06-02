#!/usr/bin/env bash
#
# Launch the Android emulator for Arcane development.
#
# Forces public DNS servers (8.8.8.8 / 1.1.1.1) because the emulator's default
# DNS proxy can fail to resolve some Cloudflare-fronted hosts (e.g. the hosted
# demo at demo.getarcane.app) even when the host machine resolves them fine.
#
# Usage:
#   ./run-emulator.sh                 # launch the default AVD ("arcane")
#   ./run-emulator.sh <avd-name>      # launch a specific AVD
#   ./run-emulator.sh arcane -wipe-data   # pass extra emulator flags through
#
set -euo pipefail

AVD="${1:-arcane}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
EMULATOR="$ANDROID_HOME/emulator/emulator"

if [[ ! -x "$EMULATOR" ]]; then
  echo "error: emulator binary not found at: $EMULATOR" >&2
  echo "       set ANDROID_HOME to your Android SDK location and retry." >&2
  exit 1
fi

if ! "$EMULATOR" -list-avds | grep -qx "$AVD"; then
  echo "error: AVD '$AVD' not found. Available AVDs:" >&2
  "$EMULATOR" -list-avds | sed 's/^/  - /' >&2
  echo "create one in Android Studio (Device Manager) or with avdmanager." >&2
  exit 1
fi

echo "Starting emulator '$AVD' with DNS 8.8.8.8,1.1.1.1 ..."
exec "$EMULATOR" -avd "$AVD" \
  -dns-server 8.8.8.8,1.1.1.1 \
  -netdelay none \
  -netspeed full \
  "${@:2}"
