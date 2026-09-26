#!/usr/bin/env bash
# Build the KA2 Settings APK without Gradle: aapt2 link -> javac -> d8 -> dex -> zipalign -> sign.
set -eu
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$ROOT/jdk"; export PATH="$JAVA_HOME/bin:$PATH"
SDK="$ROOT/sdk"
BT=$(ls -d "$SDK"/build-tools/* | head -1)
PLAT=$(ls -d "$SDK"/platforms/* | head -1)
APP="$ROOT/app"; OUT="$ROOT/out"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex"

echo "== 1/6 resources+manifest"
# The app has real resources now (its launcher icon), so they are compiled and linked in rather than
# carrying a bare manifest.
"$BT/aapt2" compile --dir "$APP/res" -o "$OUT/res.zip"
"$BT/aapt2" link -o "$OUT/base.apk" -I "$PLAT/android.jar" --manifest "$APP/AndroidManifest.xml" \
  --min-sdk-version 26 --target-sdk-version 34 "$OUT/res.zip"

echo "== 2/6 compile java"
javac -classpath "$PLAT/android.jar" -d "$OUT/classes" $(find "$APP/src" -name '*.java')

echo "== 3/6 dex (d8 desugars)"
"$BT/d8" --lib "$PLAT/android.jar" --min-api 26 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "== 4/6 add classes.dex into the apk"
python3 - "$OUT/base.apk" "$OUT/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
print("   classes.dex added")
PY

echo "== 5/6 align + sign"
"$BT/zipalign" -f 4 "$OUT/base.apk" "$OUT/aligned.apk"
# The keystore lives OUTSIDE out/, which is wiped at the top of every build: keeping it there
# minted a fresh key per build, and Android refuses to update an app whose signature changed.
KEYSTORE="$ROOT/ka2.keystore"
PASS="${KA2_KEYSTORE_PASS:?set KA2_KEYSTORE_PASS to the passphrase of ka2.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$PASS" -keypass "$PASS" \
    -alias ka2 -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=KA2 Settings, O=Hermes" >/dev/null 2>&1
  echo "   new signing key created at $KEYSTORE"
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:"$PASS" --key-pass pass:"$PASS" \
  --out "$OUT/KA2Settings.apk" "$OUT/aligned.apk"

echo "== 6/6 verify"
"$BT/apksigner" verify --print-certs "$OUT/KA2Settings.apk" | head -3
ls -l "$OUT/KA2Settings.apk" | awk '{print "   APK:", $5, "bytes"}'
echo "APK BUILT: $OUT/KA2Settings.apk"
