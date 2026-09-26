#!/usr/bin/env bash
# Android build toolchain for the settings APK, installed under /config/.hermes/apk.
# No root: JDK from a tarball, SDK from Google's cmdline-tools, then platform + build-tools.
# Deliberately Gradle-free for a small app: javac -> d8 -> aapt2 -> zipalign -> apksigner.
set -u
ROOT=/config/.hermes/apk
mkdir -p "$ROOT"
cd "$ROOT"
unzip_any() {  # unzip may be absent in this container; python3 always has zipfile
  if command -v unzip >/dev/null 2>&1; then unzip -q "$1" -d "$2"
  else python3 -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$1" "$2"; fi
}

if [ ! -x jdk/bin/java ]; then
  echo "== JDK 17 (Temurin tarball)"
  curl -sSL -o jdk.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" || { echo "JDK DOWNLOAD FAILED"; exit 1; }
  mkdir -p jdk && tar xzf jdk.tar.gz -C jdk --strip-components=1
  echo "   $(jdk/bin/java -version 2>&1 | head -1)"
else
  echo "== JDK present"
fi
export JAVA_HOME="$ROOT/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

if [ ! -x sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  echo "== Android cmdline-tools"
  curl -sSL -o cmdtools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" || { echo "CMDTOOLS DOWNLOAD FAILED"; exit 1; }
  mkdir -p sdk/cmdline-tools && unzip_any cmdtools.zip sdk/cmdline-tools
  mv sdk/cmdline-tools/cmdline-tools sdk/cmdline-tools/latest
else
  echo "== cmdline-tools present"
fi
export ANDROID_HOME="$ROOT/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

echo "== accepting licences"
yes | sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1

echo "== installing platform-tools, android-34, build-tools 34"
sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 | tail -4

echo "== toolchain summary"
echo "   java:    $(java -version 2>&1 | head -1)"
echo "   javac:   $(javac -version 2>&1)"
echo "   platform: $(ls "$ANDROID_HOME/platforms" 2>/dev/null | tr '\n' ' ')"
echo "   build-tools: $(ls "$ANDROID_HOME/build-tools" 2>/dev/null | tr '\n' ' ')"
echo "   tools:   aapt2=$(ls "$ANDROID_HOME"/build-tools/*/aapt2 2>/dev/null | head -1) d8=$(ls "$ANDROID_HOME"/build-tools/*/d8 2>/dev/null | head -1) apksigner=$(ls "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | head -1)"
echo "TOOLCHAIN DONE"
