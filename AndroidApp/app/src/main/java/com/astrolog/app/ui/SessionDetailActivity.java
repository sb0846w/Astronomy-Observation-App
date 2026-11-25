package com.astrolog.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astrolog.app.R;
import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.model.ObservingSession;
import com.astrolog.app.util.DateUtils;

import java.util.List;
import java.util.Locale;

/**
 * Shows one session with all its details and every celestial object observed in it (features b & c).
 * From here the user can edit/delete the session and add/edit objects.
 */
public class SessionDetailActivity extends AppCompatActivity implements ObservationAdapter.Listener {

    public static final String EXTRA_SESSION_ID = "session_id";

    private AstroDbHelper db;
    private long sessionId;
    private ObservingSession session;
    private ObservationAdapter adapter;

    private TextView textName, textWhen, textDetails, textObservationsHeader, textNoObservations;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_detail);
        setTitle(R.string.title_session_detail);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = AstroDbHelper.getInstance(this);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, 0);

        textName = findViewById(R.id.textName);
        textWhen = findViewById(R.id.textWhen);
        textDetails = findViewById(R.id.textDetails);
        textObservationsHeader = findViewById(R.id.textObservationsHeader);
        textNoObservations = findViewById(R.id.textNoObservations);

        RecyclerView recycler = findViewById(R.id.recyclerObservations);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ObservationAdapter(this);
        recycler.setAdapter(adapter);

        findViewById(R.id.fabAddObservation).setOnClickListener(v -> {
            Intent intent = new Intent(this, ObservationFormActivity.class);
            intent.putExtra(ObservationFormActivity.EXTRA_SESSION_ID, sessionId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        session = db.getSession(sessionId);
        if (session == null) { // e.g. deleted from elsewhere
            finish();
            return;
        }
        showSession();
        showObservations();
    }

    private void showSession() {
        textName.setText(session.getName());
        textWhen.setText(DateUtils.toDisplay(session.getDate()) + " at " + session.getTime()
                + "  •  " + session.getDurationLabel());

        StringBuilder sb = new StringBuilder();
        append(sb, "Location", session.getLocation());
        if (session.getLatitude() != null) {
            append(sb, "GPS", String.format(Locale.UK, "%.4f, %.4f", session.getLatitude(), session.getLongitude()));
        }
        append(sb, "Sky conditions", session.getSkyConditions());
        append(sb, "Description", session.getDescription());
        append(sb, "Observer", session.getObserverName());
        append(sb, "Equipment", session.getEquipment());
        append(sb, "Bortle scale", session.getBortleScale() + " (1 = darkest, 9 = city)");
        append(sb, "Seeing", session.getSeeingRating() + " / 5");
        append(sb, "Cloud status", session.isUploaded() ? "Uploaded" : "Not uploaded yet");
        textDetails.setText(sb.toString().trim());
    }

    private static void append(StringBuilder sb, String label, String value) {
        if (!TextUtils.isEmpty(value)) sb.append(label).append(": ").append(value).append('\n');
    }

    private void showObservations() {
        List<CelestialObservation> list = db.getObservationsForSession(sessionId);
        adapter.setItems(list);
        textObservationsHeader.setText(getString(R.string.observations_header, list.size()));
        textNoObservations.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onObservationClick(CelestialObservation observation) {
        Intent intent = new Intent(this, ObservationFormActivity.class);
        intent.putExtra(ObservationFormActivity.EXTRA_SESSION_ID, sessionId);
        intent.putExtra(ObservationFormActivity.EXTRA_OBSERVATION_ID, observation.getId());
        startActivity(intent);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_session_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (id == R.id.action_edit) {
            Intent intent = new Intent(this, SessionFormActivity.class);
            intent.putExtra(SessionFormActivity.EXTRA_SESSION_ID, sessionId);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_delete && session != null) {
            SessionActions.confirmDelete(this, session, this::finish);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
