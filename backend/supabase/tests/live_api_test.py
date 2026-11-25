"""Live end-to-end test of the AstroLog Supabase backend over HTTPS, making the same calls as the apps
(publishable key only, no login).

Run from the project root:
    python backend/supabase/tests/live_api_test.py backend/supabase/seed/sample-sync-payload.json

It uploads 2 test sessions with ids starting d0d0d0d0-. Nobody can delete through the API (by design),
so remove them afterwards in the Supabase SQL Editor:
    delete from public.observing_sessions where id::text like 'd0d0d0d0-%';
"""
import copy, json, sys, time, uuid, urllib.error, urllib.request

U = "https://rlauvxzzjpdkbxwcfogj.supabase.co"
K = "sb_publishable_7IfKZ7q4v_0hodY0CGxeKg_9S2KrFA1"
results = []


def call(method, path, body=None, headers=None):
    h = {"apikey": K, "Content-Type": "application/json", "Accept": "application/json"}
    h.update(headers or {})
    data = json.dumps(body).encode() if body is not None else None
    last = None
    for _ in range(6):                      # retry flaky connections, never HTTP errors
        req = urllib.request.Request(U + path, data=data, method=method, headers=h)
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                raw = r.read().decode()
                return r.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode()
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, raw
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last = e
            time.sleep(2)
    raise RuntimeError(f"network failed for {path}: {last}")


def check(name, ok, info=""):
    results.append(ok)
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f"  ({info})" if info else ""), flush=True)


def sync(device_key, sessions):
    return call("POST", "/rest/v1/rpc/sync_observing_sessions", {"p_device_key": device_key, "p_sessions": sessions})


sample = json.load(open(sys.argv[1], encoding="utf-8"))["p_sessions"]
sessions = json.loads(json.dumps(sample).replace("a1b2c3d4-", "d0d0d0d0-").replace("b1b2c3d4-", "d1d1d1d1-"))
ids = [s["id"] for s in sessions]
id_filter = f"in.({','.join(ids)})"
phone_a, phone_b = str(uuid.uuid4()), str(uuid.uuid4())   # two phones' device keys

# Android upload (CloudUploader.java)
st, res = sync(phone_a, sessions)
check("L01 bulk upload: 2 sessions + 6 observations", st == 200 and res["synced_count"] == 2
      and res["observation_count"] == 6 and res["failed_count"] == 0, f"HTTP {st}")

st, res = sync(phone_a, sessions)
st2, rows = call("GET", f"/rest/v1/celestial_observations?select=id&session_id={id_filter}")
check("L02 repeated upload keeps 6 observations (no duplicates)", st == 200 and len(rows) == 6, f"{len(rows)} rows")

bad = copy.deepcopy(sessions)
del bad[1]["session_title"]
st, res = sync(phone_a, bad)
failure = res["failed"][0] if res.get("failed") else {}
check("L03 partial failure reported per session", st == 200 and res["synced_count"] == 1
      and failure.get("error_code") == "missing_field", failure.get("message", ""))

st, res = sync(phone_b, sessions)
check("L04 another phone cannot overwrite these sessions (not_owner)",
      st == 200 and res["failed_count"] == 2 and res["failed"][0]["error_code"] == "not_owner")

st, res = sync("", sessions)
check("L05 upload without a device key is rejected", st == 400 and res.get("message") == "invalid_device_key", f"HTTP {st}")

# Direct writes with the publishable key are refused
st, _ = call("PATCH", f"/rest/v1/observing_sessions?id=eq.{ids[0]}", {"session_title": "Hacked"})
check("L06 direct PATCH refused", st in (401, 403), f"HTTP {st}")
st, _ = call("DELETE", f"/rest/v1/celestial_observations?session_id=eq.{ids[0]}")
check("L07 direct DELETE refused", st in (401, 403), f"HTTP {st}")
st, _ = call("POST", "/rest/v1/observing_sessions", {"id": str(uuid.uuid4()), "session_title": "x", "session_date": "2026-01-01",
                                                     "session_time": "10:00", "observing_location": "x", "duration_hours": 1})
check("L08 direct INSERT refused", st in (401, 403), f"HTTP {st}")

# MAUI download (CloudService.DownloadAllSessionsAsync)
st, rows = call("GET", "/rest/v1/observing_sessions?select=*,celestial_observations(*)&celestial_observations.order=sort_order"
                       f"&order=session_date.desc,session_time.desc,id&id={id_filter}&limit=100&offset=0")
nested = sum(len(s["celestial_observations"]) for s in rows) if st == 200 else -1
check("L09 MAUI download: sessions with embedded observations", st == 200 and len(rows) == 2 and nested == 6,
      f"{len(rows)} sessions, {nested} observations")
expected_keys = {"id", "session_title", "session_date", "session_time", "observing_location", "duration_hours",
                 "bortle_scale", "seeing_rating", "synced_at", "celestial_observations"}
check("L10 JSON keys match the MAUI models", st == 200 and expected_keys <= set(rows[0].keys()))
obs_keys = {"id", "celestial_object", "location_in_sky", "observation_details", "additional_comments"}
check("L11 observation keys match the MAUI model", st == 200 and obs_keys <= set(rows[0]["celestial_observations"][0].keys()))

# Search (CloudService.SearchCloudAsync)
for query, expected in [("Jupiter", 2), ("jup", 2), ("Saturn", 1), ("Moon", 1), ("Orion Nebula", 1),
                        ("Andromeda Galaxy", 1), ("Pluto", 0)]:
    st, rows = call("POST", "/rest/v1/rpc/search_observations", {"p_query": query, "p_limit": 100, "p_offset": 0})
    mine = [r for r in rows if r["session_id"] in ids] if st == 200 else []
    check(f"L12 search {query!r} -> {expected}", st == 200 and len(mine) == expected, str(len(mine)))

st, rows = call("POST", "/rest/v1/rpc/search_observations", {"p_query": "jupiter", "p_limit": 1, "p_offset": 1})
check("L13 search pagination (limit 1, offset 1) + total_count", st == 200 and len(rows) == 1 and rows[0]["total_count"] >= 2)
check("L14 search rows include session details", st == 200 and bool(rows[0]["session_title"] and rows[0]["session_date"]))
st, rows = call("POST", "/rest/v1/rpc/search_observations", {"p_query": " "})
check("L15 empty search text -> 400 search_query_required", st == 400 and rows.get("message") == "search_query_required", f"HTTP {st}")

print(f"\nLIVE TESTS: {sum(results)} passed, {len(results) - sum(results)} failed")
print("Clean up in the SQL Editor:  delete from public.observing_sessions where id::text like 'd0d0d0d0-%';")
