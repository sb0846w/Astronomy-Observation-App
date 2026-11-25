package com.astrolog.app.ui;

import android.content.Context;
import android.widget.Toast;

import com.astrolog.app.R;
import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.model.ObservingSession;
import com.astrolog.app.util.PhotoUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * Session actions shared by the list screen and the detail screen,
 * kept in one place to avoid duplicate code.
 */
public final class SessionActions {

    private SessionActions() { }

    /** Asks the user to confirm, then deletes the session, its observations and their photos. */
    public static void confirmDelete(Context context, ObservingSession session, Runnable onDeleted) {
        AstroDbHelper db = AstroDbHelper.getInstance(context);
        List<CelestialObservation> observations = db.getObservationsForSession(session.getId());

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_session_title)
                .setMessage(context.getString(R.string.delete_session_message, session.getName(), observations.size()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_delete, (dialog, which) -> {
                    for (CelestialObservation o : observations) {
                        PhotoUtils.deleteQuietly(o.getPhotoPath());
                    }
                    db.deleteSession(session.getId()); // observations go via ON DELETE CASCADE
                    Toast.makeText(context, R.string.session_deleted, Toast.LENGTH_SHORT).show();
                    if (onDeleted != null) onDeleted.run();
                })
                .show();
    }
}
