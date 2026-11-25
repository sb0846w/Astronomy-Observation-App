package com.astrolog.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astrolog.app.R;
import com.astrolog.app.model.ObservingSession;
import com.astrolog.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows the list of sessions on the main screen (feature b).
 * Keeps the full list plus a filtered copy so the search box can narrow it down.
 */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {

    /** Lets MainActivity decide what happens on tap / edit / delete. */
    public interface Listener {
        void onSessionClick(ObservingSession session);
        void onEditSession(ObservingSession session);
        void onDeleteSession(ObservingSession session);
    }

    private final Listener listener;
    private final List<ObservingSession> allSessions = new ArrayList<>();
    private final List<ObservingSession> visibleSessions = new ArrayList<>();
    private String currentQuery = "";

    public SessionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setSessions(List<ObservingSession> sessions) {
        allSessions.clear();
        allSessions.addAll(sessions);
        filter(currentQuery);
    }

    /** Case-insensitive search on session name, location and observer. */
    @SuppressWarnings("NotifyDataSetChanged") // whole list changes when the query changes
    public void filter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        visibleSessions.clear();
        for (ObservingSession s : allSessions) {
            if (currentQuery.isEmpty() || matches(s, currentQuery)) {
                visibleSessions.add(s);
            }
        }
        notifyDataSetChanged();
    }

    private static boolean matches(ObservingSession s, String q) {
        return contains(s.getName(), q) || contains(s.getLocation(), q) || contains(s.getObserverName(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    public int getVisibleCount() {
        return visibleSessions.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        ObservingSession s = visibleSessions.get(position);
        h.name.setText(s.getName());
        h.when.setText(DateUtils.toDisplay(s.getDate()) + "  •  " + s.getTime());
        h.location.setText(s.getLocation());

        int count = s.getObservationCount();
        String objects = count == 1 ? "1 object" : count + " objects";
        h.meta.setText(s.getDurationLabel() + "  •  " + objects
                + (s.getEquipment() != null ? "  •  " + s.getEquipment() : ""));
        h.uploaded.setVisibility(s.isUploaded() ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> listener.onSessionClick(s));

        // Overflow (⋮) button gives direct Edit / Delete from the list
        h.more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.inflate(R.menu.menu_session_item);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_edit) {
                    listener.onEditSession(s);
                    return true;
                } else if (item.getItemId() == R.id.action_delete) {
                    listener.onDeleteSession(s);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return visibleSessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name, when, location, meta;
        final ImageView uploaded;
        final ImageButton more;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.textSessionName);
            when = v.findViewById(R.id.textSessionWhen);
            location = v.findViewById(R.id.textSessionLocation);
            meta = v.findViewById(R.id.textSessionMeta);
            uploaded = v.findViewById(R.id.imageUploaded);
            more = v.findViewById(R.id.buttonMore);
        }
    }
}
