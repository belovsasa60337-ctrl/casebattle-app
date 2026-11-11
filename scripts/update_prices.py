"""Собирает цены скинов (Steam) в prices.json: {"market_hash_name": цена_в_рублях}."""
import json, urllib.request, gzip, io, sys, time

def get(url, timeout=120):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Accept-Encoding": "gzip"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
        if r.headers.get("Content-Encoding") == "gzip" or data[:2] == b"\x1f\x8b":
            data = gzip.decompress(data)
        return json.loads(data)

rate = 80.0
try:
    rate = float(get("https://open.er-api.com/v6/latest/USD")["rates"]["RUB"])
except Exception as e:
    print("rate fallback", e)
print("USD->RUB", rate)

STEAM_CAP = 1500  # на Steam цена упирается в потолок ~$1800, для таких берём сторонние площадки
prices, src = {}, None
for url in ["https://prices.csgotrader.app/latest/prices_v6.json"]:
    try:
        src = get(url); print("loaded", url, len(src)); break
    except Exception as e:
        print("fail", url, e)
if not src:
    sys.exit("no price source")

def num(v):
    try:
        v = float(v)
        return v if v > 0 else None
    except Exception:
        return None

def steam_price(o):
    st = o.get("steam") or {}
    if isinstance(st, dict):
        for k in ("last_7d", "last_24h", "last_30d", "last_90d", "last_ever"):
            v = num(st.get(k))
            if v: return v
    return num(st)

def other_price(o):
    best = None
    for k, v in o.items():
        if k == "steam": continue
        cand = None
        if isinstance(v, dict):
            for kk in ("starting_at", "price", "highest_order", "last_7d", "last_24h"):
                x = v.get(kk)
                if isinstance(x, dict): x = x.get("price")
                cand = num(x)
                if cand: break
        else:
            cand = num(v)
        if cand and (best is None or cand > best): best = cand
    return best

sample = next(iter(src.items()))
print("sample", json.dumps(sample)[:600])
for name, o in src.items():
    if not isinstance(o, dict): continue
    p = steam_price(o)
    if p is None or p >= STEAM_CAP:
        q = other_price(o)
        if q and (p is None or q > p): p = q
    if p:
        prices[name] = round(min(p * rate, 1_500_000))

print("items", len(prices))
top = sorted(prices.items(), key=lambda x: -x[1])[:10]
for n, v in top: print(v, n)
json.dump({"t": int(time.time()), "rate": rate, "p": prices}, open("prices.json", "w"), ensure_ascii=False, separators=(",", ":"))
