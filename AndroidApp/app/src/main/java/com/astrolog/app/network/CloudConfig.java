package com.astrolog.app.network;

/**
 * Cloud backend settings (feature d).
 *
 * Backend: Supabase (hosted PostgreSQL) used through its REST API with plain HTTPS + JSON,
 * so no extra SDK is needed. Schema, security rules and functions: backend/supabase/migrations.
 *
 * No login: the app uses only the PUBLISHABLE key, which is public by design. The database
 * lets that key READ everything and WRITE only through sync_observing_sessions(), which
 * validates the data and checks this phone's device key. NEVER put the secret /
 * service_role key in the app or in Git.
 * The hybrid .NET MAUI app uses the same project URL and publishable key.
 */
public final class CloudConfig {

    /** Supabase project URL, without a trailing slash. */
    public static final String SUPABASE_URL = "https://rlauvxzzjpdkbxwcfogj.supabase.co";

    /** Supabase publishable (public) API key. */
    public static final String SUPABASE_PUBLISHABLE_KEY = "sb_publishable_7IfKZ7q4v_0hodY0CGxeKg_9S2KrFA1";

    /** Bulk upload of all sessions + observations (PostgreSQL function, see sync_function.sql). */
    public static final String SYNC_ENDPOINT = SUPABASE_URL + "/rest/v1/rpc/sync_observing_sessions";

    public static final int TIMEOUT_MS = 15_000;

    private CloudConfig() { }

    public static boolean isConfigured() {
        return SUPABASE_URL.startsWith("https://") && !SUPABASE_PUBLISHABLE_KEY.isEmpty();
    }
}
