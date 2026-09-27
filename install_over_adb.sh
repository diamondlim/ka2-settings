#!/bin/bash
# Install / update the KA2 Settings app on the car's head unit over ADB.
#
# Why this exists: the head unit has no working APK installer path, so the app's own "Install vX.Y"
# button cannot complete there (Android needs an installer activity to take the package archive, and
# this unit has none that accepts it). The supported update path on the head unit is ADB from a host
# that the unit has authorised - this host, or any machine whose adb key the unit trusts.
#
# Usage:
#   ./install_over_adb.sh                  # newest dist/*.apk, or build if there is none
#   ./install_over_adb.sh <apk>            # a specific APK
#   ./install_over_adb.sh --build          # rebuild first, then install the result
#   ./install_over_adb.sh --serial IP:5555 # talk to a different unit
#   ./install_over_adb.sh --downgrade      # allow a lower versionCode (adb install -d)
#
# It refuses to install to anything that does not identify itself as the BYD unit, and it verifies the
# installed versionCode afterwards rather than trusting adb's exit status.
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ADB="${ADB:-$(command -v adb || true)}"
[ -x "$ADB" ] || ADB="$(command -v adb || true)"
AAPT="$HERE/sdk/build-tools/34.0.0/aapt2"
PKG=com.hermes.ka2settings
CANDIDATES=("10.0.3.102:5555")

APK=""; SERIAL=""; BUILD=0; DOWNGRADE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=1 ;;
    --downgrade) DOWNGRADE=1 ;;
    --serial) SERIAL="${2:-}"; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) APK="$1" ;;
  esac
  shift
done

say() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[ -n "$ADB" ] || die "no adb found. Set ADB=/path/to/adb, or run ../setup_toolchain.sh on a host that has one."
say "adb:    $ADB"

if [ "$BUILD" = 1 ]; then
  say "building..."
  ( cd "$HERE" && ./build.sh ) || die "build failed"
fi

if [ -z "$APK" ]; then
  APK=$(ls -1t "$HERE"/dist/*.apk 2>/dev/null | head -1)
  [ -n "$APK" ] || die "no APK in dist/ - build one (./build.sh) or pass a path"
fi
[ -f "$APK" ] || die "no such APK: $APK"

# what the APK says it is (never trust the file name)
if [ -x "$AAPT" ]; then
  BADGING=$("$AAPT" dump badging "$APK" 2>/dev/null)
  APK_CODE=$(printf '%s' "$BADGING" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
  APK_NAME=$(printf '%s' "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)
  APK_PKG=$(printf '%s' "$BADGING" | sed -n "s/package: name='\([^']*\)'.*/\1/p" | head -1)
else
  APK_CODE=""; APK_NAME=""; APK_PKG=""
fi
[ -z "$APK_PKG" ] || [ "$APK_PKG" = "$PKG" ] || die "$APK is package $APK_PKG, expected $PKG"
say "apk:    $APK (v${APK_NAME:-?} code ${APK_CODE:-?}, $(du -h "$APK" | cut -f1))"

# find the unit: a device that is already ready, else connect to the known address
SERIAL="${SERIAL:-$( "$ADB" devices | awk '$2 == "device" && $1 ~ /:/ {print $1; exit}' )}"
if [ -z "$SERIAL" ]; then
  for candidate in "${CANDIDATES[@]}"; do
    say "connecting to $candidate ..."
    out=$( "$ADB" connect "$candidate" 2>&1 )
    say "  $out"
    case "$out" in
      *"connected to"*) SERIAL="$candidate"; break ;;
      *"already connected"*) SERIAL="$candidate"; break ;;
    esac
  done
fi
[ -n "$SERIAL" ] || die "no head unit reachable. Is the car awake (the unit powers down ~60 s after lock) and wireless ADB on?"

STATE=$( "$ADB" devices | awk -v s="$SERIAL" '$1 == s {print $2}' )
case "$STATE" in
  device) ;;
  unauthorized) die "$SERIAL is unauthorised: run 'adb kill-server; $ADB connect $SERIAL' and accept the prompt on the head unit (or re-pair wireless debugging)." ;;
  "") die "$SERIAL vanished after connect." ;;
  *) die "$SERIAL is $STATE." ;;
esac

MODEL=$( "$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
REL=$( "$ADB" -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
say "unit:   $SERIAL  model='$MODEL' Android $REL"
case "$MODEL" in
  *BYD*) ;;
  *) die "refusing to install: model '$MODEL' does not look like the car's head unit" ;;
esac

BEFORE=$( "$ADB" -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')
say "installed now: v${BEFORE:-none}"

FLAGS=(-r -g)                       # replace, and grant the runtime permissions it needs (no taps in the car)
[ "$DOWNGRADE" = 1 ] && FLAGS+=(-d)
say "installing..."
OUT=$( "$ADB" -s "$SERIAL" install "${FLAGS[@]}" "$APK" 2>&1)
printf '%s\n' "$OUT" | sed 's/^/  /'
case "$OUT" in
  *Success*) ;;
  *INSTALL_FAILED_VERSION_DOWNGRADE*) die "the unit already has a newer version. Use --downgrade only if you mean to go backwards." ;;
  *INSTALL_FAILED_UPDATE_INCOMPATIBLE*) die "signature mismatch: this APK was signed with a different key than the installed app. Matching keystore required - do not re-sign." ;;
  *INSTALL_PARSE_FAILED*) die "the unit rejected the APK itself (bad or truncated build)." ;;
  *) die "install did not report success." ;;
esac

AFTER=$( "$ADB" -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1 | tr -d '\r')
AFTER_NAME=$( "$ADB" -s "$SERIAL" shell dumpsys package "$PKG" 2>/dev/null | sed -n 's/.*versionName=\([^ ]*\).*/\1/p' | head -1 | tr -d '\r')
[ -z "$APK_CODE" ] || [ "$AFTER" = "$APK_CODE" ] || die "install reported success but the unit still reports versionCode ${AFTER:-none} (wanted $APK_CODE)"
say "verified: $PKG v${AFTER_NAME:-?} code ${AFTER:-?} on $SERIAL"

# restart the app so the new build is what is running (and re-reads the box)
"$ADB" -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
"$ADB" -s "$SERIAL" shell am start -n "$PKG/.SettingsActivity" >/dev/null 2>&1
say "restarted $PKG on the head unit"
