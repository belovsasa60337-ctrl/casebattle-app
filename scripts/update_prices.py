"""Собирает цены скинов в prices.json: {"p": {"market_hash_name": цена_в_рублях}}.
Основа — цены Steam Market. Steam не даёт ставить цену выше ~$1800, поэтому для
дорогих скинов берём цену со сторонних площадок (Buff163 / CSFloat / Skinport)."""
import json, urllib.request, gzip, time

def get(url, timeout=120):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Accept-Encoding": "gzip", "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read()
        if r.headers.get("Content-Encoding") == "gzip" or data[:2] == b"\x1f\x8b": data = gzip.decompress(data)
        return json.loads(data)

def num(v):
    try:
        v = float(v); return v if v > 0 else None
    except Exception:
        return None

def pick(o, keys):
    if not isinstance(o, dict): return num(o)
    for k in keys:
        v = o.get(k)
        if isinstance(v, dict):
            v = pick(v, ("price", "starting_at", "last_7d", "last_24h"))
        v = num(v)
        if v: return v
    return None

rate = 80.0
try: rate = float(get("https://open.er-api.com/v6/latest/USD")["rates"]["RUB"])
except Exception as e: print("rate fallback", e)
print("USD->RUB", rate)

steam = get("https://prices.csgotrader.app/latest/steam.json")
print("steam", len(steam))
extra = {}
for src in ("buff163", "csfloat", "skinport"):
    try:
        d = get(f"https://prices.csgotrader.app/latest/{src}.json")
        extra[src] = d
        print(src, len(d), json.dumps(next(iter(d.items())))[:200])
    except Exception as e:
        print("skip", src, repr(e)[:120])

STEAM_CAP, MAX_RUB = 1500, 5_000_000
names = set(steam) | set().union(*[set(d) for d in extra.values()]) if extra else set(steam)
prices = {}
for n in names:
    p = pick(steam.get(n), ("last_7d", "last_24h", "last_30d", "last_90d"))
    if p is None or p >= STEAM_CAP:
        alt = [pick(d.get(n), ("starting_at", "price", "highest_order", "last_7d")) for d in extra.values()]
        alt = [a for a in alt if a]
        if alt and (p is None or max(alt) > p): p = max(alt)
    if not p or " | " not in n and not n.startswith("★"): continue
    if n.startswith(("Sticker", "Patch", "Sealed Graffiti", "Music Kit", "Souvenir", "Charm")): continue
    prices[n] = round(min(p * rate, MAX_RUB))

print("items", len(prices))
for n, v in sorted(prices.items(), key=lambda x: -x[1])[:15]: print(v, n)
for n in ("AK-47 | Redline (Field-Tested)", "AWP | Asiimov (Field-Tested)", "★ Karambit | Doppler (Factory New)", "★ Karambit | Fade (Factory New)", "Glock-18 | Candy Apple (Factory New)"):
    print("check", n, prices.get(n))
json.dump({"t": int(time.time()), "rate": round(rate, 2), "p": prices}, open("prices.json", "w"), ensure_ascii=False, separators=(",", ":"))
