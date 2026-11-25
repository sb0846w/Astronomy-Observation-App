package com.astrolog.app.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.SQLException;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.model.ObservingSession;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uploads ALL sessions and their observations to Supabase in ONE request (feature d).
 *
 * How it works (no login – only the publishable key is used):
 *  1. Read every session + its observations from SQLite.
 *  2. Build one JSON body:
 *       { "p_device_key": "<this phone's secret UUID>",
 *         "p_sessions": [ { ...session..., "observations": [ ... ] }, ... ] }
 *  3. POST it to  /rest/v1/rpc/sync_observing_sessions  (a PostgreSQL function).
 *     Sessions and observations are identified by UUIDs created on the phone, so uploading
 *     again updates the same rows instead of creating duplicates. The device key proves
 *     this phone uploaded them first, so no other phone can overwrite them.
 *  4. The reply lists which sessions were synced and which failed (with a reason).
 *     Only the synced sessions are marked as uploaded (green cloud icon).
 *     Local SQLite data is never deleted.
 *
 * Network calls are not allowed on the main (UI) thread, so the work runs on a background
 * executor and the result is posted back to the main thread through a Handler.
 */
public class CloudUploader {

    /** Callback so the Activity can show a user-friendly result message. */
    public interface UploadCallback {
        void onSuccess(int sessionCount, int observationCount);
        void onError(String message);
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 2_000;

    private static final String PREFS = "cloud_sync";
    private static final String KEY_DEVICE_KEY = "device_key";

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CloudUploader(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * A random secret created once per installation and kept in private app storage.
     * The cloud stores only its hash and uses it to check that later uploads of a session
     * come from the same phone. (Clearing the app's data creates a new key; sessions uploaded
     * with the old key can then no longer be changed from this phone.)
     */
    private String getDeviceKey() {
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = prefs.getString(KEY_DEVICE_KEY, null);
        if (key == null) {
            key = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_KEY, key).apply();
        }
        return key;
    }

    /** True if the phone currently has a working internet connection. */
    public boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public void uploadAll(UploadCallback callback) {
        executor.execute(() -> {
            try {
                AstroDbHelper db = AstroDbHelper.getInstance(appContext);
                List<ObservingSession> sessions = db.getAllSessions();
                if (sessions.isEmpty()) {
                    postError(callback, "There are no sessions to upload yet.");
                    return;
                }

                // Build the JSON payload
                JSONArray sessionArray = new JSONArray();
                Map<String, ObservingSession> byCloudId = new HashMap<>();
                for (ObservingSession s : sessions) {
                    List<CelestialObservation> observations = db.getObservationsForSession(s.getId());
                    sessionArray.put(sessionToJson(s, observations));
                    byCloudId.put(s.getCloudId(), s);
                }
                String body = new JSONObject()
                        .put("p_device_key", getDeviceKey())
                        .put("p_sessions", sessionArray)
                        .toString();

                // Send it (retried automatically if the connection drops – safe because uploads are idempotent)
                HttpJson.Response response = sendWithRetry(body);
                if (!response.isSuccess()) {
                    postError(callback, "The upload was rejected. " + response.errorMessage());
                    return;
                }

                handleResult(new JSONObject(response.body), byCloudId, db, callback);

            } catch (IOException e) {
                postError(callback, "Could not reach the cloud service after " + MAX_ATTEMPTS
                        + " tries. Your data is safe on this phone – tap Upload again later. (" + e.getMessage() + ")");
            } catch (JSONException e) {
                postError(callback, "Could not read the data for the upload.");
            } catch (SQLException e) {
                postError(callback, "Could not read your saved sessions from the phone's database.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                postError(callback, "The upload was cancelled.");
            }
        });
    }

    /**
     * Sends the upload, retrying up to {@link #MAX_ATTEMPTS} times when the network fails or the
     * server is temporarily unavailable (HTTP 429 / 5xx), waiting a little longer each time.
     * Retrying never creates duplicates because every row is identified by its UUID.
     * Errors caused by the data itself (HTTP 4xx) are returned straight away.
     */
    private HttpJson.Response sendWithRetry(String body) throws IOException, InterruptedException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpJson.Response response = HttpJson.post(CloudConfig.SYNC_ENDPOINT, body);
                boolean temporary = response.code == 429 || response.code >= 500;
                if (!temporary || attempt == MAX_ATTEMPTS) return response;
            } catch (IOException e) {
                lastError = e;               // no connection, timeout, connection reset…
                if (attempt == MAX_ATTEMPTS || !isOnline()) break;
            }
            Thread.sleep(RETRY_DELAY_MS * attempt);   // 2 s, then 4 s
        }
        throw lastError != null ? lastError : new IOException("No response from the server.");
    }

    /** Marks the synced sessions as uploaded and reports the outcome to the Activity. */
    private void handleResult(JSONObject result, Map<String, ObservingSession> byCloudId,
                              AstroDbHelper db, UploadCallback callback) throws JSONException {
        JSONArray synced = result.getJSONArray("synced");
        JSONArray failed = result.getJSONArray("failed");

        List<ObservingSession> syncedSessions = new ArrayList<>();
        for (int i = 0; i < synced.length(); i++) {
            ObservingSession s = byCloudId.get(synced.getJSONObject(i).getString("id"));
            if (s != null) syncedSessions.add(s);
        }
        if (!syncedSessions.isEmpty()) {
            db.markUploaded(syncedSessions, System.currentTimeMillis());
        }

        if (failed.length() == 0) {
            final int sessionCount = syncedSessions.size();
            final int obsCount = result.getInt("observation_count");
            mainHandler.post(() -> callback.onSuccess(sessionCount, obsCount));
            return;
        }

        // Some (or all) sessions failed: say which ones and why
        StringBuilder sb = new StringBuilder();
        sb.append(synced.length()).append(" of ").append(synced.length() + failed.length())
          .append(" sessions were uploaded. These could not be uploaded:\n");
        for (int i = 0; i < failed.length(); i++) {
            JSONObject f = failed.getJSONObject(i);
            ObservingSession s = byCloudId.get(f.optString("id"));
            sb.append("\n• ").append(s != null ? s.getName() : f.optString("id", "?"))
              .append(": ").append(f.optString("message", f.optString("error_code")));
        }
        postError(callback, sb.toString());
    }

    private void postError(UploadCallback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    /**
     * Converts a session and its observations into the JSON shape sync_observing_sessions()
     * expects. Keys match the Supabase column names (snake_case). Empty optional fields are
     * left out and stored as NULL.
     */
    static JSONObject sessionToJson(ObservingSession s, List<CelestialObservation> observations) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", s.getCloudId());
        json.put("session_title", s.getName());
        json.put("session_date", s.getDate());
        json.put("session_time", s.getTime());
        json.put("observing_location", s.getLocation());
        json.put("duration_hours", s.getDurationHours());
        json.putOpt("description", emptyToNull(s.getDescription()));
        json.putOpt("sky_conditions", emptyToNull(s.getSkyConditions()));
        json.putOpt("observer_name", emptyToNull(s.getObserverName()));
        json.putOpt("equipment", emptyToNull(s.getEquipment()));
        json.put("bortle_scale", s.getBortleScale());
        json.put("seeing_rating", s.getSeeingRating());
        if (s.getLatitude() != null && s.getLongitude() != null) {
            json.put("latitude", s.getLatitude());
            json.put("longitude", s.getLongitude());
        }

        JSONArray array = new JSONArray();
        for (CelestialObservation o : observations) {
            JSONObject oj = new JSONObject();
            oj.put("id", o.getCloudId());
            oj.put("celestial_object", o.getObjectName());
            oj.put("location_in_sky", o.getSkyLocation());
            oj.put("observation_details", o.getDetails());
            oj.putOpt("additional_comments", emptyToNull(o.getComments()));
            oj.putOpt("object_type", emptyToNull(o.getObjectType()));
            oj.putOpt("observed_time", emptyToNull(o.getObservedTime()));
            oj.put("has_photo", o.getPhotoPath() != null);
            array.put(oj);
        }
        json.put("observations", array);
        return json;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
