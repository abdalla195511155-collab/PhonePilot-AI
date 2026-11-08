#!/system/bin/sh
# Manual APK build for PhonePilot AI (stage 1) - Java version
# Uses: aapt2 + javac + d8 + apksigner
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/app/src/main"

ANDROID_JAR="$HOME/android-sdk/platforms/android-34/android.jar"
MIN_SDK=24
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME="1.0.0"
KEYSTORE="$HOME/.android/debug.keystore"
KS_ALIAS="androiddebugkey"
KS_PASS="android"
export KS_PASS

fail() { echo "build-apk: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || fail "$1 is not installed."; }

for tool in aapt2 d8 apksigner keytool jar; do need "$tool"; done
[ -f "$ANDROID_JAR" ] || fail "android.jar not found at $ANDROID_JAR"
[ -f AndroidManifest.xml ] || fail "AndroidManifest.xml not found"

PACKAGE=$(sed -n 's/.*package="\([^"]*\)".*/\1/p' AndroidManifest.xml | head -n 1)
[ -n "$PACKAGE" ] || fail "No package in AndroidManifest.xml"

echo "Package: $PACKAGE"

rm -rf build
mkdir -p build/classes build/dex build/gen build/res

# Step 1: Compile resources with aapt2
echo "Compiling resources..."
if [ -d res ] && [ -n "$(ls -A res 2>/dev/null)" ]; then
    aapt2 compile --dir res -o build/res/res.zip
    aapt2 link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
        --java build/gen --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
        --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
        -o build/base.apk build/res/res.zip
else
    aapt2 link -I "$ANDROID_JAR" --manifest AndroidManifest.xml \
        --java build/gen --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" \
        --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
        -o build/base.apk
fi

# Step 2: Collect Java sources
echo "Collecting Java sources..."
find java -name '*.java' > build/sources.txt
[ -s build/sources.txt ] || fail "No Java sources found under java/"

# Step 3: Compile Java sources with javac
echo "Compiling Java sources..."
CLASSPATH="$ANDROID_JAR"

javac -source 8 -target 8 -bootclasspath "$CLASSPATH" -d build/classes @build/sources.txt

# Step 4: Convert to DEX with d8
echo "Converting to DEX..."
d8 --min-api "$MIN_SDK" --lib "$ANDROID_JAR" --output build/dex build/classes

# Step 5: Package APK
echo "Packaging APK..."
cp build/base.apk build/unsigned.apk
for dex in build/dex/classes*.dex; do
    jar --update --file build/unsigned.apk -C build/dex "$(basename "$dex")"
done

# Step 6: Sign
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
        -alias "$KS_ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" -nopassword
fi

OUT="build/${PACKAGE}.apk"
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" \
    --ks-pass env:KS_PASS --key-pass env:KS_PASS --out "$OUT" build/unsigned.apk
apksigner verify "$OUT"

echo ""
echo "APK: $OUT"
echo "Package: $PACKAGE"
echo "Build completed successfully."
