"""Get the Uthmani Quran text for the offline app. Tries several sources and keeps a copy in the repo.
Writes: www/quran-uthmani.json, www/quran-data.js (and quran-source.json as a cache)."""
import json, os, sys, time, urllib.request

CACHE = 'quran-source.json'
OUT_JSON = 'www/quran-uthmani.json'
OUT_JS = 'www/quran-data.js'


def get(url, tries=4, timeout=60):
    last = None
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={
                'User-Agent': 'Mozilla/5.0 (compatible; ayah-marker-build)',
                'Accept': 'application/json'})
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode('utf-8'))
        except Exception as e:                      # network error, bad status, bad json
            last = e
            print('   try %d failed: %s' % (i + 1, e))
            time.sleep(4 * (i + 1))
    raise last


def valid(surahs):
    try:
        return len(surahs) == 114 and sum(len(s['ayahs']) for s in surahs) == 6236
    except Exception:
        return False


def from_cache():
    with open(CACHE, encoding='utf-8') as f:
        return json.load(f)['data']['surahs']


def from_alquran_full():
    return get('https://api.alquran.cloud/v1/quran/quran-uthmani')['data']['surahs']


def from_alquran_per_surah():
    out = []
    for n in range(1, 115):
        d = get('https://api.alquran.cloud/v1/surah/%d/quran-uthmani' % n)
        out.append({'number': n, 'ayahs': d['data']['ayahs']})
        time.sleep(0.2)
    return out


def from_quran_com():
    verses = get('https://api.quran.com/api/v4/quran/verses/uthmani')['verses']
    by = {}
    for v in verses:
        s, a = (int(x) for x in v['verse_key'].split(':'))
        by.setdefault(s, {})[a] = v['text_uthmani']
    bism = by[1][1]                                  # the Bismillah (ayah 1 of Al-Fatiha)
    out, counter = [], 0
    for s in range(1, 115):
        ayahs = []
        for a in sorted(by[s]):
            counter += 1
            text = by[s][a]
            if a == 1 and s not in (1, 9):           # same layout as the app expects
                text = bism + ' ' + text
            ayahs.append({'number': counter, 'numberInSurah': a, 'text': text})
        out.append({'number': s, 'ayahs': ayahs})
    return out


SOURCES = []
if os.path.exists(CACHE):
    SOURCES.append(('saved copy in the repo (%s)' % CACHE, from_cache, False))
SOURCES += [
    ('api.alquran.cloud (whole Quran)', from_alquran_full, True),
    ('api.alquran.cloud (surah by surah)', from_alquran_per_surah, True),
    ('api.quran.com', from_quran_com, True),
]

surahs, used, fresh = None, None, False
for name, fn, is_network in SOURCES:
    print('== trying:', name)
    try:
        data = fn()
        if valid(data):
            surahs, used, fresh = data, name, is_network
            break
        print('   not valid (surah/ayah count is wrong)')
    except Exception as e:
        print('   failed:', e)

if surahs is None:
    sys.exit('!! Could not get the Quran text from any source')

print('Quran text OK from "%s": %d surahs, %d ayahs' % (used, len(surahs), sum(len(s['ayahs']) for s in surahs)))

os.makedirs('www', exist_ok=True)
with open(OUT_JSON, 'w', encoding='utf-8') as f:
    json.dump({'data': {'surahs': surahs}}, f, ensure_ascii=False)
with open(OUT_JS, 'w', encoding='utf-8') as f:
    f.write('window.QURAN_LOCAL=' + json.dumps([{'ayahs': s['ayahs']} for s in surahs], ensure_ascii=False) + ';')
if fresh:                                            # keep a copy so later builds never need the internet
    with open(CACHE, 'w', encoding='utf-8') as f:
        json.dump({'data': {'surahs': surahs}}, f, ensure_ascii=False)
print('written:', OUT_JSON, OUT_JS)
