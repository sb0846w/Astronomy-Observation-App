package com.astrolog.app.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Tiny helper for JSON POST requests to Supabase with HttpURLConnection.
 * Must be called from a background thread.
 */
final class HttpJson {

    /** Status code + body of a response (the body is read for errors too). */
    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }

        boolean isSuccess() {
            return code >= 200 && code < 300;
        }

        /**
         * Turns a Supabase error body into a readable sentence.
         * Errors look like {"code":"22023","message":"empty_payload","details":"..."}.
         */
        String errorMessage() {
            try {
                JSONObject json = new JSONObject(body);
                String details = json.optString("details", "");
                String message = json.optString("message", "");
                String text = !details.isEmpty() && !"null".equals(details) ? details : message;
                if (!text.isEmpty()) return text + " (HTTP " + code + ")";
            } catch (JSONException ignored) {
                // not JSON – fall through
            }
            return "The server replied with HTTP " + code + ".";
        }
    }

    private HttpJson() { }

    /** POSTs a JSON body. The publishable key goes in the "apikey" header (no login needed). */
    static Response post(String url, String jsonBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("apikey", CloudConfig.SUPABASE_PUBLISHABLE_KEY);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(CloudConfig.TIMEOUT_MS);
            conn.setReadTimeout(CloudConfig.TIMEOUT_MS);
            conn.setDoOutput(true);

            try (OutputStream out = conn.getOutputStream()) {
                out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            return new Response(code, readStream(stream));
        } finally {
            conn.disconnect();
        }
    }

    private static String readStream(InputStream in) throws IOException {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
