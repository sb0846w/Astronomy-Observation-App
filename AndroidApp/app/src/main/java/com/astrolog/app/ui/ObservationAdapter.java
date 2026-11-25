package com.astrolog.app.ui;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.astrolog.app.R;
import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.util.PhotoUtils;

import java.util.ArrayList;
import java.util.List;

/** Lists the celestial objects recorded for one session (feature c). */
public class ObservationAdapter extends RecyclerView.Adapter<ObservationAdapter.ViewHolder> {

    public interface Listener {
        void onObservationClick(CelestialObservation observation);
    }

    private final Listener listener;
    private final List<CelestialObservation> items = new ArrayList<>();

    public ObservationAdapter(Listener listener) {
        this.listener = listener;
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public void setItems(List<CelestialObservation> observations) {
        items.clear();
        items.addAll(observations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_observation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        CelestialObservation o = items.get(position);
        h.name.setText(o.getObjectName());

        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(o.getObjectType())) meta.append(o.getObjectType()).append("  •  ");
        meta.append(o.getSkyLocation());
        if (!TextUtils.isEmpty(o.getObservedTime())) meta.append("  •  ").append(o.getObservedTime());
        h.meta.setText(meta);
        h.details.setText(o.getDetails());

        // Show the photo thumbnail if there is one, otherwise a star icon
        Bitmap thumb = PhotoUtils.loadScaled(o.getPhotoPath(), 160);
        if (thumb != null) {
            h.thumb.setImageTintList(null);
            h.thumb.setImageBitmap(thumb);
        } else {
            // Restore the tint in case this recycled row showed a photo before
            h.thumb.setImageTintList(h.defaultTint);
            h.thumb.setImageResource(R.drawable.ic_star);
        }

        h.itemView.setOnClickListener(v -> listener.onObservationClick(o));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name, meta, details;
        final ImageView thumb;
        final android.content.res.ColorStateList defaultTint;

        ViewHolder(View v) {
            super(v);
            name = v.findViewById(R.id.textObjectName);
            meta = v.findViewById(R.id.textObjectMeta);
            details = v.findViewById(R.id.textObjectDetails);
            thumb = v.findViewById(R.id.imageThumb);
            defaultTint = thumb.getImageTintList();
        }
    }
}
