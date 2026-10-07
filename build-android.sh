#!/usr/bin/env bash
set -euo pipefail

echo "== Install dependencies"
npm install

echo "== Prepare web files"
mkdir -p www
cp index.html www/index.html
cp node_modules/sql.js/dist/sql-wasm.js www/
cp node_modules/sql.js/dist/sql-wasm.wasm www/

echo "== Download Quran text (bundled for offline use)"
curl -fsSL --retry 5 --retry-delay 5 \
  "https://api.alquran.cloud/v1/quran/quran-uthmani" -o www/quran-uthmani.json
python3 - <<'PY'
import json
d = json.load(open('www/quran-uthmani.json', encoding='utf-8'))
s = d['data']['surahs']
assert len(s) == 114, 'surah count %d' % len(s)
n = sum(len(x['ayahs']) for x in s)
assert n == 6236, 'ayah count %d' % n
print('Quran text OK:', len(s), 'surahs,', n, 'ayahs')

# the same text as a script file: the app reads it with no network and no fetch
out = [{'ayahs': x['ayahs']} for x in s]
with open('www/quran-data.js', 'w', encoding='utf-8') as f:
    f.write('window.QURAN_LOCAL=' + json.dumps(out, ensure_ascii=False) + ';')
print('quran-data.js written')
PY

echo "== Embed Quran text + sql.js inside the page (fully offline)"
python3 embed-offline.py

echo "== Add Android platform"
npx cap add android

echo "== Custom Java (keep screen on + save to Downloads)"
PKG_DIR=android/app/src/main/java/com/idris/ayahmarker
mkdir -p "$PKG_DIR"
cp MainActivity.java SaveFilePlugin.java "$PKG_DIR/"

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
