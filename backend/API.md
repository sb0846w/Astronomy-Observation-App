# AstroLog API reference

Base URL: `https://rlauvxzzjpdkbxwcfogj.supabase.co`. **No login:** every request uses only the publishable key.

| Header | When | Value |
|---|---|---|
| `apikey` | **every** request | the publishable key `sb_publishable_…` |
| `Content-Type` | requests with a body | `application/json` |
| `Prefer` | optional | `count=exact` (total number of rows in the `Content-Range` header) |

These are Supabase's auto-generated REST endpoints (PostgREST) plus two database functions.

---

## Bulk upload (Android app)

```http
POST /rest/v1/rpc/sync_observing_sessions
apikey: sb_publishable_…
Content-Type: application/json
```

**Request:** the phone's device key plus every session with every observation. The full example is in `supabase/seed/sample-sync-payload.json` (add `p_device_key`).
```json
{
  "p_device_key": "3f2b7c1e-8d4a-4e6b-9c0d-1a2b3c4d5e6f",
  "p_sessions": [
    {
      "id": "a1b2c3d4-0001-4000-8000-000000000001",
      "session_title": "Winter planets from the park",
      "session_date": "2026-11-20",
      "session_time": "20:30",
      "observing_location": "Greenwich Park, London",
      "duration_hours": 2,
      "description": "Quick session after work.",
      "sky_conditions": "Mostly clear",
      "observer_name": "Demo Observer",
      "equipment": "Telescope",
      "bortle_scale": 8,
      "seeing_rating": 3,
      "latitude": 51.4769,
      "longitude": -0.0005,
      "observations": [
        {
          "id": "b1b2c3d4-0001-4000-8000-000000000001",
          "celestial_object": "Jupiter",
          "location_in_sky": "Gemini, east",
          "observation_details": "Two cloud belts clearly visible, four moons in a line.",
          "additional_comments": "130 mm reflector, 10 mm eyepiece",
          "object_type": "Planet",
          "observed_time": "20:45",
          "has_photo": false
        }
      ]
    }
  ]
}
```

| Field | Required | Rule |
|---|---|---|
| `p_device_key` | ✔ | a UUID generated once per phone and kept private |
| `id` (session and observation) | ✔ | UUID generated on the phone, never changes |
| `session_title` | ✔ | 1–120 characters |
| `session_date` | ✔ | `yyyy-MM-dd`, a real date |
| `session_time` | ✔ | `HH:mm` (24 h) |
| `observing_location` | ✔ | 1–200 characters |
| `duration_hours` | ✔ | 0.1–24 |
| `description`, `sky_conditions`, `observer_name`, `equipment` | — | empty becomes NULL |
| `bortle_scale` / `seeing_rating` | — | 1–9 / 1–5 |
| `latitude` + `longitude` | — | both or neither, valid ranges |
| `observations[].celestial_object`, `location_in_sky`, `observation_details` | ✔ | non-blank |
| `observations[].additional_comments`, `object_type`, `observed_time`, `has_photo` | — | `observed_time` is `HH:mm` |

Limits: 200 sessions per request, 500 observations per session.

**Response `200`** (once the request itself is valid, even if some sessions failed):
```json
{
  "synced_at": "2026-11-25T14:02:11.123+00:00",
  "synced_count": 1,
  "failed_count": 1,
  "observation_count": 3,
  "synced": [ { "id": "a1b2c3d4-0001-4000-8000-000000000001", "observations": 3 } ],
  "failed": [ { "id": "a1b2c3d4-0002-4000-8000-000000000002",
                "error_code": "missing_field",
                "message": "Missing required field: session_title" } ]
}
```

Behaviour:

- **Idempotent:** the same UUIDs update the same rows.
- **Complete replace per session:** observations missing from `observations` are deleted in the cloud.
- **Per-session transactions:** a failed session is rolled back completely and the others are kept.
- **Device-key ownership:** a session can only be changed with the key that first uploaded it.

---

## Reading (MAUI app, or anyone with the publishable key)

| What | Request |
|---|---|
| Sessions, newest first, paged | `GET /rest/v1/observing_sessions?select=*&order=session_date.desc,session_time.desc,id&limit=20&offset=0` |
| One session | `GET /rest/v1/observing_sessions?id=eq.<uuid>` |
| Sessions **with** their observations (what the MAUI app uses) | `GET /rest/v1/observing_sessions?select=*,celestial_observations(*)&celestial_observations.order=sort_order&order=session_date.desc,session_time.desc,id&limit=100&offset=0` |
| Observations of one session | `GET /rest/v1/celestial_observations?session_id=eq.<uuid>&order=sort_order` |
| Observations with session details | `GET /rest/v1/celestial_observations?select=*,observing_sessions(session_title,session_date,observing_location,observer_name)` |
| Simple name filter | `GET /rest/v1/celestial_observations?celestial_object=ilike.*jupiter*` |
| Total count | add header `Prefer: count=exact` → `Content-Range: 0-19/57` |

Writing directly (`POST`, `PATCH` or `DELETE` on these tables) returns **401** `42501 permission denied`. Writes are only possible through the upload function.

## Search

```http
POST /rest/v1/rpc/search_observations
apikey: sb_publishable_…
Content-Type: application/json

{ "p_query": "Jupiter", "p_limit": 20, "p_offset": 0 }
```

- Case-insensitive partial match on `celestial_object`: "jup", "JUPITER" and "Jupiter" all work.
- Exact name matches first, then the newest session.
- `%` and `_` typed by the user are searched literally.
- `p_limit` is 1–100 (default 20) and `p_offset` is ≥ 0 (default 0).

`200` → an array (`[]` when nothing matches):
```json
[
  {
    "observation_id": "b1b2c3d4-0001-4000-8000-000000000001",
    "celestial_object": "Jupiter",
    "object_type": "Planet",
    "location_in_sky": "Gemini, east",
    "observation_details": "Two cloud belts clearly visible, four moons in a line.",
    "additional_comments": "130 mm reflector, 10 mm eyepiece",
    "observed_time": "20:45",
    "has_photo": false,
    "session_id": "a1b2c3d4-0001-4000-8000-000000000001",
    "session_title": "Winter planets from the park",
    "session_date": "2026-11-20",
    "session_time": "20:30",
    "observing_location": "Greenwich Park, London",
    "observer_name": "Demo Observer",
    "equipment": "Telescope",
    "sky_conditions": "Mostly clear",
    "total_count": 2
  }
]
```
Next page: increase `p_offset` by `p_limit` while `p_offset + p_limit < total_count`.

---

## Errors

Whole-request errors use PostgREST's format:
```json
{ "code": "22023", "message": "search_query_required", "details": "Type at least one character to search for.", "hint": null }
```

| Situation | HTTP | `code` / `message` |
|---|---|---|
| Missing or invalid `p_device_key` | 400 | `22023` / `invalid_device_key` |
| Upload body not an array, empty, or over 200 sessions | 400 | `22023` / `invalid_payload`, `empty_payload`, `too_many_sessions` |
| Empty or too-long search text, bad `p_limit` / `p_offset` | 400 | `22023` / `search_query_required`, `search_query_too_long`, `invalid_limit`, `invalid_offset` |
| Direct `INSERT` / `UPDATE` / `DELETE` on a table | 401 | `42501` permission denied for table |
| Invalid UUID in a REST filter (`id=eq.abc`) | 400 | `22P02` invalid input syntax for type uuid |
| Wrong or missing `apikey` | 401 | invalid API key |
| No network or timeout | — | handled in the apps ("Could not reach the cloud service") |

Per-session upload errors (inside `failed[]`, the HTTP status is still 200):

| `error_code` | Meaning |
|---|---|
| `missing_field` | a required field is missing (`message` names it) |
| `invalid_format` | a UUID, number or true/false value can't be read |
| `invalid_date` | e.g. `2026-02-30` |
| `invalid_value` | breaks a rule: blank title, Bortle 12, time `25:00`, duration 0… |
| `value_out_of_range` | number too large for its column |
| `duplicate_id` | the same observation id twice in one session |
| `observation_id_conflict` | the observation id belongs to a different session |
| `not_owner` | the session was first uploaded from another phone (different device key) |
| `invalid_session` / `invalid_observations` / `too_many_observations` | wrong JSON shape, or more than 500 observations |
| `database_error` | anything unexpected (`message` contains the database text) |
