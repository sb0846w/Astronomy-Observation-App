package com.astrolog.app.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.model.ObservingSession;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local SQLite storage (features b and c).
 *
 * Two tables:
 *   sessions      – one row per observing session
 *   observations  – one row per celestial object, linked to a session by session_id
 *
 * The foreign key uses ON DELETE CASCADE so deleting a session also deletes its observations.
 * A single shared instance is used (singleton) so the whole app talks to one connection.
 */
public class AstroDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "astrolog.db";
    // Version 2 adds observations.cloud_id (stable UUID used by the Supabase sync)
    private static final int DATABASE_VERSION = 2;

    // ---- sessions table ----
    public static final String TABLE_SESSIONS = "sessions";
    private static final String S_ID = "id";
    private static final String S_CLOUD_ID = "cloud_id";
    private static final String S_NAME = "name";
    private static final String S_DATE = "session_date";
    private static final String S_TIME = "session_time";
    private static final String S_LOCATION = "location";
    private static final String S_DURATION = "duration_hours";
    private static final String S_DESCRIPTION = "description";
    private static final String S_SKY = "sky_conditions";
    private static final String S_OBSERVER = "observer_name";
    private static final String S_EQUIPMENT = "equipment";
    private static final String S_BORTLE = "bortle_scale";
    private static final String S_SEEING = "seeing_rating";
    private static final String S_LAT = "latitude";
    private static final String S_LNG = "longitude";
    private static final String S_UPLOADED_AT = "uploaded_at";

    // ---- observations table ----
    public static final String TABLE_OBSERVATIONS = "observations";
    private static final String O_ID = "id";
    private static final String O_CLOUD_ID = "cloud_id";
    private static final String O_SESSION_ID = "session_id";
    private static final String O_OBJECT_NAME = "object_name";
    private static final String O_OBJECT_TYPE = "object_type";
    private static final String O_SKY_LOCATION = "sky_location";
    private static final String O_DETAILS = "details";
    private static final String O_COMMENTS = "comments";
    private static final String O_TIME = "observed_time";
    private static final String O_PHOTO = "photo_path";

    private static AstroDbHelper instance;

    /** Returns the single shared helper. Uses the application context to avoid leaking activities. */
    public static synchronized AstroDbHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AstroDbHelper(context.getApplicationContext());
        }
        return instance;
    }

    private AstroDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        // SQLite has foreign keys switched off by default – we need them for ON DELETE CASCADE
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_SESSIONS + " ("
                + S_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + S_CLOUD_ID + " TEXT NOT NULL UNIQUE, "
                + S_NAME + " TEXT NOT NULL, "
                + S_DATE + " TEXT NOT NULL, "
                + S_TIME + " TEXT NOT NULL, "
                + S_LOCATION + " TEXT NOT NULL, "
                + S_DURATION + " REAL NOT NULL, "
                + S_DESCRIPTION + " TEXT, "
                + S_SKY + " TEXT, "
                + S_OBSERVER + " TEXT, "
                + S_EQUIPMENT + " TEXT, "
                + S_BORTLE + " INTEGER, "
                + S_SEEING + " INTEGER, "
                + S_LAT + " REAL, "
                + S_LNG + " REAL, "
                + S_UPLOADED_AT + " INTEGER)");

        db.execSQL("CREATE TABLE " + TABLE_OBSERVATIONS + " ("
                + O_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + O_CLOUD_ID + " TEXT NOT NULL UNIQUE, "
                + O_SESSION_ID + " INTEGER NOT NULL, "
                + O_OBJECT_NAME + " TEXT NOT NULL, "
                + O_OBJECT_TYPE + " TEXT, "
                + O_SKY_LOCATION + " TEXT NOT NULL, "
                + O_DETAILS + " TEXT NOT NULL, "
                + O_COMMENTS + " TEXT, "
                + O_TIME + " TEXT, "
                + O_PHOTO + " TEXT, "
                + "FOREIGN KEY(" + O_SESSION_ID + ") REFERENCES " + TABLE_SESSIONS + "(" + S_ID + ") ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_obs_session ON " + TABLE_OBSERVATIONS + "(" + O_SESSION_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade step by step so the user's sessions and observations are kept.
        if (oldVersion < 2) {
            // Give every existing observation its own stable UUID for the cloud sync
            db.execSQL("ALTER TABLE " + TABLE_OBSERVATIONS + " ADD COLUMN " + O_CLOUD_ID + " TEXT");
            try (Cursor c = db.query(TABLE_OBSERVATIONS, new String[]{O_ID}, null, null, null, null, null)) {
                while (c.moveToNext()) {
                    ContentValues v = new ContentValues();
                    v.put(O_CLOUD_ID, UUID.randomUUID().toString());
                    db.update(TABLE_OBSERVATIONS, v, O_ID + " = ?", new String[]{String.valueOf(c.getLong(0))});
                }
            }
            db.execSQL("CREATE UNIQUE INDEX idx_obs_cloud_id ON " + TABLE_OBSERVATIONS + "(" + O_CLOUD_ID + ")");
            // Sessions uploaded to the old backend must be uploaded again to the new one
            db.execSQL("UPDATE " + TABLE_SESSIONS + " SET " + S_UPLOADED_AT + " = NULL");
        }
    }

    // =====================================================================
    // Sessions
    // =====================================================================

    /** Inserts a new session and returns its new row id (or -1 on failure). */
    public long insertSession(ObservingSession session) {
        long id = getWritableDatabase().insert(TABLE_SESSIONS, null, sessionToValues(session));
        session.setId(id);
        return id;
    }

    /** Updates an existing session. Any edit clears the "uploaded" flag so the user knows to re-upload. */
    public boolean updateSession(ObservingSession session) {
        ContentValues values = sessionToValues(session);
        values.putNull(S_UPLOADED_AT);
        int rows = getWritableDatabase().update(TABLE_SESSIONS, values,
                S_ID + " = ?", new String[]{String.valueOf(session.getId())});
        return rows == 1;
    }

    /** Deletes a session. Its observations are removed automatically by ON DELETE CASCADE. */
    public boolean deleteSession(long sessionId) {
        int rows = getWritableDatabase().delete(TABLE_SESSIONS,
                S_ID + " = ?", new String[]{String.valueOf(sessionId)});
        return rows == 1;
    }

    /** Deletes every session and observation (used by the "Reset database" menu option). */
    public void deleteAllSessions() {
        getWritableDatabase().delete(TABLE_SESSIONS, null, null);
    }

    public ObservingSession getSession(long sessionId) {
        try (Cursor c = getReadableDatabase().query(TABLE_SESSIONS, null,
                S_ID + " = ?", new String[]{String.valueOf(sessionId)}, null, null, null)) {
            return c.moveToFirst() ? cursorToSession(c) : null;
        }
    }

    /**
     * Returns all sessions, newest first, each with a count of its observations.
     * The count comes from a sub-query so we don't need one query per row.
     */
    public List<ObservingSession> getAllSessions() {
        List<ObservingSession> list = new ArrayList<>();
        String sql = "SELECT s.*, (SELECT COUNT(*) FROM " + TABLE_OBSERVATIONS + " o WHERE o."
                + O_SESSION_ID + " = s." + S_ID + ") AS obs_count FROM " + TABLE_SESSIONS + " s "
                + "ORDER BY s." + S_DATE + " DESC, s." + S_TIME + " DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            int countIndex = c.getColumnIndexOrThrow("obs_count");
            while (c.moveToNext()) {
                ObservingSession s = cursorToSession(c);
                s.setObservationCount(c.getInt(countIndex));
                list.add(s);
            }
        }
        return list;
    }

    /** Marks the given sessions as uploaded at the given time (called after a successful upload). */
    public void markUploaded(List<ObservingSession> sessions, long timestamp) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put(S_UPLOADED_AT, timestamp);
            for (ObservingSession s : sessions) {
                db.update(TABLE_SESSIONS, values, S_ID + " = ?", new String[]{String.valueOf(s.getId())});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private ContentValues sessionToValues(ObservingSession s) {
        ContentValues v = new ContentValues();
        v.put(S_CLOUD_ID, s.getCloudId());
        v.put(S_NAME, s.getName());
        v.put(S_DATE, s.getDate());
        v.put(S_TIME, s.getTime());
        v.put(S_LOCATION, s.getLocation());
        v.put(S_DURATION, s.getDurationHours());
        v.put(S_DESCRIPTION, s.getDescription());
        v.put(S_SKY, s.getSkyConditions());
        v.put(S_OBSERVER, s.getObserverName());
        v.put(S_EQUIPMENT, s.getEquipment());
        v.put(S_BORTLE, s.getBortleScale());
        v.put(S_SEEING, s.getSeeingRating());
        if (s.getLatitude() != null && s.getLongitude() != null) {
            v.put(S_LAT, s.getLatitude());
            v.put(S_LNG, s.getLongitude());
        } else {
            v.putNull(S_LAT);
            v.putNull(S_LNG);
        }
        return v;
    }

    private ObservingSession cursorToSession(Cursor c) {
        ObservingSession s = new ObservingSession();
        s.setId(c.getLong(c.getColumnIndexOrThrow(S_ID)));
        s.setCloudId(c.getString(c.getColumnIndexOrThrow(S_CLOUD_ID)));
        s.setName(c.getString(c.getColumnIndexOrThrow(S_NAME)));
        s.setDate(c.getString(c.getColumnIndexOrThrow(S_DATE)));
        s.setTime(c.getString(c.getColumnIndexOrThrow(S_TIME)));
        s.setLocation(c.getString(c.getColumnIndexOrThrow(S_LOCATION)));
        s.setDurationHours(c.getDouble(c.getColumnIndexOrThrow(S_DURATION)));
        s.setDescription(c.getString(c.getColumnIndexOrThrow(S_DESCRIPTION)));
        s.setSkyConditions(c.getString(c.getColumnIndexOrThrow(S_SKY)));
        s.setObserverName(c.getString(c.getColumnIndexOrThrow(S_OBSERVER)));
        s.setEquipment(c.getString(c.getColumnIndexOrThrow(S_EQUIPMENT)));
        s.setBortleScale(c.getInt(c.getColumnIndexOrThrow(S_BORTLE)));
        s.setSeeingRating(c.getInt(c.getColumnIndexOrThrow(S_SEEING)));

        int latIdx = c.getColumnIndexOrThrow(S_LAT);
        int lngIdx = c.getColumnIndexOrThrow(S_LNG);
        if (!c.isNull(latIdx) && !c.isNull(lngIdx)) {
            s.setLatitude(c.getDouble(latIdx));
            s.setLongitude(c.getDouble(lngIdx));
        }
        int upIdx = c.getColumnIndexOrThrow(S_UPLOADED_AT);
        s.setUploadedAt(c.isNull(upIdx) ? null : c.getLong(upIdx));
        return s;
    }

    // =====================================================================
    // Observations
    // =====================================================================

    public long insertObservation(CelestialObservation o) {
        long id = getWritableDatabase().insert(TABLE_OBSERVATIONS, null, observationToValues(o));
        o.setId(id);
        touchSession(o.getSessionId());
        return id;
    }

    public boolean updateObservation(CelestialObservation o) {
        int rows = getWritableDatabase().update(TABLE_OBSERVATIONS, observationToValues(o),
                O_ID + " = ?", new String[]{String.valueOf(o.getId())});
        touchSession(o.getSessionId());
        return rows == 1;
    }

    public boolean deleteObservation(CelestialObservation o) {
        int rows = getWritableDatabase().delete(TABLE_OBSERVATIONS,
                O_ID + " = ?", new String[]{String.valueOf(o.getId())});
        touchSession(o.getSessionId());
        return rows == 1;
    }

    public CelestialObservation getObservation(long observationId) {
        try (Cursor c = getReadableDatabase().query(TABLE_OBSERVATIONS, null,
                O_ID + " = ?", new String[]{String.valueOf(observationId)}, null, null, null)) {
            return c.moveToFirst() ? cursorToObservation(c) : null;
        }
    }

    /** All observations for one session, in the order they were seen. */
    public List<CelestialObservation> getObservationsForSession(long sessionId) {
        List<CelestialObservation> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_OBSERVATIONS, null,
                O_SESSION_ID + " = ?", new String[]{String.valueOf(sessionId)},
                null, null, O_TIME + " ASC, " + O_ID + " ASC")) {
            while (c.moveToNext()) {
                list.add(cursorToObservation(c));
            }
        }
        return list;
    }

    /** A change to a session's observations means the cloud copy is out of date. */
    private void touchSession(long sessionId) {
        ContentValues v = new ContentValues();
        v.putNull(S_UPLOADED_AT);
        getWritableDatabase().update(TABLE_SESSIONS, v, S_ID + " = ?", new String[]{String.valueOf(sessionId)});
    }

    private ContentValues observationToValues(CelestialObservation o) {
        ContentValues v = new ContentValues();
        v.put(O_CLOUD_ID, o.getCloudId());
        v.put(O_SESSION_ID, o.getSessionId());
        v.put(O_OBJECT_NAME, o.getObjectName());
        v.put(O_OBJECT_TYPE, o.getObjectType());
        v.put(O_SKY_LOCATION, o.getSkyLocation());
        v.put(O_DETAILS, o.getDetails());
        v.put(O_COMMENTS, o.getComments());
        v.put(O_TIME, o.getObservedTime());
        v.put(O_PHOTO, o.getPhotoPath());
        return v;
    }

    private CelestialObservation cursorToObservation(Cursor c) {
        CelestialObservation o = new CelestialObservation();
        o.setId(c.getLong(c.getColumnIndexOrThrow(O_ID)));
        o.setCloudId(c.getString(c.getColumnIndexOrThrow(O_CLOUD_ID)));
        o.setSessionId(c.getLong(c.getColumnIndexOrThrow(O_SESSION_ID)));
        o.setObjectName(c.getString(c.getColumnIndexOrThrow(O_OBJECT_NAME)));
        o.setObjectType(c.getString(c.getColumnIndexOrThrow(O_OBJECT_TYPE)));
        o.setSkyLocation(c.getString(c.getColumnIndexOrThrow(O_SKY_LOCATION)));
        o.setDetails(c.getString(c.getColumnIndexOrThrow(O_DETAILS)));
        o.setComments(c.getString(c.getColumnIndexOrThrow(O_COMMENTS)));
        o.setObservedTime(c.getString(c.getColumnIndexOrThrow(O_TIME)));
        o.setPhotoPath(c.getString(c.getColumnIndexOrThrow(O_PHOTO)));
        return o;
    }
}
