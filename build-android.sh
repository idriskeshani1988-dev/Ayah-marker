#!/usr/bin/env bash
set -euo pipefail

echo "== Install dependencies"
npm install

echo "== Prepare web files"
mkdir -p www
cp index.html www/index.html
cp node_modules/sql.js/dist/sql-wasm.js www/
cp node_modules/sql.js/dist/sql-wasm.wasm www/

echo "== Quran text (bundled for offline use)"
python3 fetch-quran.py

# keep a copy of the text in the repo, so a later build never depends on the internet
if [ -f quran-source.json ] && ! git ls-files --error-unmatch quran-source.json >/dev/null 2>&1; then
  git config user.name "github-actions"
  git config user.email "actions@users.noreply.github.com"
  git add quran-source.json
  git commit -m "Save Quran text copy for offline builds" || true
  git push || echo "(could not save the copy, continuing)"
fi

echo "== Add Android platform"
npx cap add android

echo "== Custom Java (keep screen on + save to Downloads + audio folder)"
PKG_DIR=android/app/src/main/java/com/idris/ayahmarker
mkdir -p "$PKG_DIR"
cp MainActivity.java SaveFilePlugin.java AudioFolderPlugin.java "$PKG_DIR/"

echo "== Allow the audio folder in the main storage (All files access)"
MANIFEST=android/app/src/main/AndroidManifest.xml
if ! grep -q "MANAGE_EXTERNAL_STORAGE" "$MANIFEST"; then
  sed -i 's#<application#<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />\n    <application#' "$MANIFEST"
fi
grep -n "MANAGE_EXTERNAL_STORAGE" "$MANIFEST" || { echo "!! permission was not added"; exit 1; }

echo "== Launcher icons"
python3 -m pip install --quiet pillow --break-system-packages 2>/dev/null || python3 -m pip install --quiet pillow || sudo apt-get install -y python3-pil
python3 make-icons.py

echo "== Sync + build"
npx cap sync android

echo "== Check the offline files are inside the app"
ASSETS=android/app/src/main/assets/public
for f in quran-data.js quran-uthmani.json sql-wasm.js sql-wasm.wasm index.html; do
  if [ ! -s "$ASSETS/$f" ]; then
    echo "!! MISSING in the app: $f"
    exit 1
  fi
  echo "ok: $f ($(wc -c < "$ASSETS/$f") bytes)"
done
cd android
./gradlew assembleDebug --no-daemon
