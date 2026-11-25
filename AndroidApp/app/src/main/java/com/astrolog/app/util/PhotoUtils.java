package com.astrolog.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Helpers for attaching a photo to an observation (feature f).
 * Photos are copied into the app's private storage so they survive even if the
 * original is deleted from the gallery.
 */
public final class PhotoUtils {

    private PhotoUtils() { }

    /** Creates a new empty file in app storage for the camera to write into. */
    public static File createPhotoFile(Context context) {
        File dir = new File(context.getFilesDir(), "photos");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "obs_" + System.currentTimeMillis() + ".jpg");
    }

    /** Copies a picked image (content:// uri) into app storage and returns the new file path. */
    public static String copyToAppStorage(Context context, Uri source) throws IOException {
        File target = createPhotoFile(context);
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Cannot open image");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return target.getAbsolutePath();
    }

    /**
     * Loads a scaled-down bitmap so large camera photos don't cause an OutOfMemoryError.
     * First reads only the image size, then picks a power-of-two sample size.
     */
    public static Bitmap loadScaled(String path, int maxSizePx) {
        if (path == null || !new File(path).exists()) return null;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);

        int sample = 1;
        while (opts.outWidth / (sample * 2) >= maxSizePx || opts.outHeight / (sample * 2) >= maxSizePx) {
            sample *= 2;
        }
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, opts);
    }

    public static void deleteQuietly(String path) {
        if (path != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }
}
