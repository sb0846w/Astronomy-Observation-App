# Android integration (AstroLog)

**Flow:** create session → add observations → saved in SQLite → **Upload** (cloud icon) → confirmation.

The real app is **Java** and uses plain `HttpURLConnection` + `org.json`, so there's no SDK, no extra Gradle dependency and **no login**. Each step shows the Java code **actually in the app**, followed by a **Kotlin** version of the same call for reference.

| File | Role |
|---|---|
| `network/CloudConfig.java` | project URL, publishable key, upload endpoint |
| `network/HttpJson.java` | one JSON POST helper (adds the `apikey` header, reads error replies) |
| `network/CloudUploader.java` | device key, builds the payload, calls the upload function, marks synced sessions |
| `data/AstroDbHelper.java` | SQLite (sessions 1 ──< observations). Version 2 added `observations.cloud_id` (UUID) and keeps existing data |
| `ui/MainActivity.java` | existing Upload menu item → shows the result dialog (unchanged UI) |

## 1. Configuration

```java
// network/CloudConfig.java
public static final String SUPABASE_URL = "https://rlauvxzzjpdkbxwcfogj.supabase.co";
public static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_…";   // publishable only, never the secret key
public static final String SYNC_ENDPOINT = SUPABASE_URL + "/rest/v1/rpc/sync_observing_sessions";
```

```kotlin
// Kotlin
object CloudConfig {
    const val SUPABASE_URL = "https://rlauvxzzjpdkbxwcfogj.supabase.co"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_…"
    const val SYNC_ENDPOINT = "$SUPABASE_URL/rest/v1/rpc/sync_observing_sessions"
}
```

## 2. Stable UUIDs in SQLite

- `sessions.cloud_id` and `observations.cloud_id` hold a UUID created when the record is first made (`UUID.randomUUID()` in the model constructors).
- Editing reuses the loaded object, so the UUID never changes. That's what makes repeat uploads update instead of duplicate.
- `sessions.uploaded_at` is the local **sync status**: set when a session is synced, and cleared when the session or any of its observations is edited.

## 3. Device key (ownership without a login)

```java
// CloudUploader.getDeviceKey()
SharedPreferences prefs = appContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE);
String key = prefs.getString("device_key", null);
if (key == null) {
    key = UUID.randomUUID().toString();            // created once per installation
    prefs.edit().putString("device_key", key).apply();
}
```

```kotlin
// Kotlin
fun deviceKey(context: Context): String {
    val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    return prefs.getString("device_key", null) ?: UUID.randomUUID().toString()
        .also { prefs.edit { putString("device_key", it) } }
}
```

## 4. Build the bulk-upload payload

```java
// CloudUploader.sessionToJson(...) – shortened
JSONObject s = new JSONObject();
s.put("id", session.getCloudId());
s.put("session_title", session.getName());
s.put("session_date", session.getDate());          // "yyyy-MM-dd"
s.put("session_time", session.getTime());          // "HH:mm"
s.put("observing_location", session.getLocation());
s.put("duration_hours", session.getDurationHours());
s.putOpt("description", emptyToNull(session.getDescription()));   // optional → left out if empty
// … sky_conditions, observer_name, equipment, bortle_scale, seeing_rating, latitude, longitude

JSONArray obs = new JSONArray();
for (CelestialObservation o : observations) {
    JSONObject oj = new JSONObject();
    oj.put("id", o.getCloudId());
    oj.put("celestial_object", o.getObjectName());
    oj.put("location_in_sky", o.getSkyLocation());
    oj.put("observation_details", o.getDetails());
    oj.putOpt("additional_comments", emptyToNull(o.getComments()));
    // … object_type, observed_time, has_photo
    obs.put(oj);
}
s.put("observations", obs);

String body = new JSONObject()
        .put("p_device_key", getDeviceKey())
        .put("p_sessions", sessionArray)
        .toString();
```

```kotlin
// Kotlin
fun ObservingSession.toJson(observations: List<CelestialObservation>) = JSONObject().apply {
    put("id", cloudId)
    put("session_title", name)
    put("session_date", date)
    put("session_time", time)
    put("observing_location", location)
    put("duration_hours", durationHours)
    putOpt("description", description?.takeIf { it.isNotBlank() })
    put("observations", JSONArray(observations.map { o ->
        JSONObject().apply {
            put("id", o.cloudId)
            put("celestial_object", o.objectName)
            put("location_in_sky", o.skyLocation)
            put("observation_details", o.details)
            putOpt("additional_comments", o.comments?.takeIf { it.isNotBlank() })
        }
    }))
}
val body = JSONObject()
    .put("p_device_key", deviceKey(context))
    .put("p_sessions", JSONArray(sessions.map { it.toJson(db.observationsFor(it)) }))
```

## 5. Send it and handle the result

```java
// CloudUploader.uploadAll() – runs on a background executor, result posted to the main thread
HttpJson.Response response = HttpJson.post(CloudConfig.SYNC_ENDPOINT, body);
if (!response.isSuccess()) { /* whole request rejected: show response.errorMessage() */ }

JSONObject result = new JSONObject(response.body);
// result.synced = [{"id": "...", "observations": 3}, ...]
// result.failed = [{"id": "...", "error_code": "missing_field", "message": "..."}]
db.markUploaded(sessionsWhoseIdIsIn(result.getJSONArray("synced")), System.currentTimeMillis());
```

```kotlin
// Kotlin (coroutines + OkHttp)
suspend fun uploadAll(): JSONObject = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(CloudConfig.SYNC_ENDPOINT)
        .header("apikey", CloudConfig.SUPABASE_PUBLISHABLE_KEY)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
        .build()
    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) throw IOException("Upload rejected: HTTP ${resp.code} ${resp.body?.string()}")
        JSONObject(resp.body!!.string()).also { result ->
            val synced = result.getJSONArray("synced")
            val ids = (0 until synced.length()).map { synced.getJSONObject(it).getString("id") }.toSet()
            db.markUploaded(sessions.filter { it.cloudId in ids })
        }
    }
}
```

What the user sees (existing `MainActivity` dialogs):

| Outcome | Message |
|---|---|
| all synced | "2 session(s) and 6 celestial object observation(s) are now saved in the cloud and can be viewed in the AstroExplorer app." |
| some failed | "1 of 2 sessions were uploaded. These could not be uploaded: • Dark sky trip: Missing required field: session_title" |
| request rejected | "The upload was rejected. … (HTTP 400)" |
| offline (checked before sending) | "You appear to be offline. Connect to Wi-Fi or mobile data and try again. Your data is safe on this phone." |
| network error or timeout | "Could not reach the cloud service. …" |
| nothing to upload | "There are no sessions to upload yet." |

## 6. Avoiding duplicates and handling failures

- The same UUIDs mean the same rows: pressing Upload ten times leaves one copy.
- Only sessions in `synced` get the green cloud. Failed ones are simply sent again next time.
- Nothing is deleted from SQLite after uploading.
- The upload runs in one request, so it either reaches the server (and each session succeeds or fails on its own) or it doesn't (network error: nothing is marked, and it's safe to retry).
