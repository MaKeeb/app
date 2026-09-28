#!/usr/bin/env bash
# Runs every module's commonTest on the iOS simulator (Kotlin/Native): the runtime the keyboard
# extension uses. Boots the test device if needed and shuts it down again only if this script
# booted it, so a simulator you are using is never touched.
#
#   scripts/ios-sim-test.sh                 # default device: "iPhone 17"
#   MAKEEB_IOS_TEST_DEVICE="iPhone Air" scripts/ios-sim-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DEVICE_NAME="${MAKEEB_IOS_TEST_DEVICE:-iPhone 17}"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode-beta.app/Contents/Developer}"
if [ -z "${JAVA_HOME:-}" ]; then
  source app/ios/Config/find-java.sh
fi

UDID=$(xcrun simctl list devices available -j | python3 -c '
import json, sys
name = sys.argv[1]
for runtime, devices in json.load(sys.stdin)["devices"].items():
    if "iOS" not in runtime: continue
    for d in devices:
        if d["name"] == name: print(d["udid"]); raise SystemExit
' "$DEVICE_NAME")
if [ -z "$UDID" ]; then
  echo "No available iOS simulator named '$DEVICE_NAME' (set MAKEEB_IOS_TEST_DEVICE)." >&2
  exit 1
fi

BOOTED_HERE=0
if ! xcrun simctl list devices | grep -q "$UDID) (Booted)"; then
  echo "Booting $DEVICE_NAME ($UDID)"
  xcrun simctl boot "$UDID"
  BOOTED_HERE=1
fi
xcrun simctl bootstatus "$UDID" -b > /dev/null

status=0
./gradlew iosSimulatorArm64Test -Pmakeeb.iosTestDevice="$UDID" --continue "$@" || status=$?

if [ "$BOOTED_HERE" = 1 ]; then
  echo "Shutting down $DEVICE_NAME"
  xcrun simctl shutdown "$UDID"
fi
exit $status
