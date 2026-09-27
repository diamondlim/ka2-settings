#!/bin/bash
# Ship a KA2 Settings build: gate, build, archive, and install to the car's head unit over ADB.
#
# The head unit cannot install an APK itself (no installer activity), so "shipping" means ADB from this
# host. The GitHub release - which is what the phone's in-app updater reads - is a separate, deliberate
# step, hence --publish rather than doing it every time.
#
#   ./ship.sh                      # test, build, archive, install to the head unit
#   ./ship.sh --publish "notes"    # ... and publish the GitHub release with those notes
#   ./ship.sh --no-install         # build and archive only (e.g. the car is away)
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
export PATH="$HERE/jdk/bin:$PATH"
PUBLISH=0; INSTALL=1; NOTES=""
while [ $# -gt 0 ]; do
  case "$1" in
    --publish) PUBLISH=1; NOTES="${2:-}"; [ $# -gt 1 ] && shift ;;
    --no-install) INSTALL=0 ;;
    *) NOTES="$1" ;;
  esac
  shift
done

say() { printf '\n== %s\n' "$*"; }

say "gate: off-device tests"
rm -rf "$HERE/out/test" && mkdir -p "$HERE/out/test"
javac -classpath "$HERE/sdk/platforms/android-34/android.jar" -d "$HERE/out/test" \
  "$HERE"/app/src/com/hermes/ka2settings/*.java "$HERE"/app/test/ClientParseTest.java || exit 1
java -cp "$HERE/out/test:$HERE/sdk/platforms/android-34/android.jar" ClientParseTest | tail -3
[ "${PIPESTATUS[0]}" = 0 ] || { echo "tests FAILED - not shipping"; exit 1; }

say "build"
( cd "$HERE" && ./build.sh | tail -3 ) || exit 1

BADGING=$("$HERE/sdk/build-tools/34.0.0/aapt2" dump badging "$HERE/out/KA2Settings.apk")
NAME=$(printf '%s' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
CODE=$(printf '%s' "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
APK="$HERE/dist/KA2Settings-v${NAME}-${CODE}.apk"
cp "$HERE/out/KA2Settings.apk" "$APK"
say "archived $APK (v$NAME code $CODE, $(du -h "$APK" | cut -f1))"

if [ "$INSTALL" = 1 ]; then
  say "install to the head unit over ADB"
  "$HERE/install_over_adb.sh" "$APK" || exit 1
else
  say "skipping the install (--no-install)"
fi

if [ "$PUBLISH" = 1 ]; then
  say "publish the GitHub release"
  "$HERE/publish_to_github.sh" "${NOTES:-v$NAME}" || exit 1
fi

say "done: v$NAME (code $CODE)"
