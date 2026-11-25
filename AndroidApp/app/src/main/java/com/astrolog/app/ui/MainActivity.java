package com.astrolog.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.astrolog.app.R;
import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.ObservingSession;
import com.astrolog.app.network.CloudConfig;
import com.astrolog.app.network.CloudUploader;
import com.astrolog.app.util.ThemeHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;

/**
 * Home screen: lists every observing session (feature b), with search,
 * upload to cloud (feature d), dark mode toggle (feature f) and reset.
 */
public class MainActivity extends AppCompatActivity implements SessionAdapter.Listener {

    private AstroDbHelper db;
    private SessionAdapter adapter;
    private TextView textEmpty;
    private LinearProgressIndicator progressUpload;
    private CloudUploader uploader;
    private boolean uploading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.sessions_title);

        db = AstroDbHelper.getInstance(this);
        uploader = new CloudUploader(this);

        textEmpty = findViewById(R.id.textEmpty);
        progressUpload = findViewById(R.id.progressUpload);

        RecyclerView recycler = findViewById(R.id.recyclerSessions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SessionAdapter(this);
        recycler.setAdapter(adapter);

        findViewById(R.id.fabAddSession).setOnClickListener(v ->
                startActivity(new Intent(this, SessionFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions(); // refresh whenever we come back from another screen
    }

    private void loadSessions() {
        adapter.setSessions(db.getAllSessions());
        updateEmptyState();
    }

    private void updateEmptyState() {
        textEmpty.setVisibility(adapter.getVisibleCount() == 0 ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchView.clearFocus();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    adapter.filter(newText);
                    updateEmptyState();
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_upload) {
            uploadAll();
            return true;
        } else if (id == R.id.action_theme) {
            ThemeHelper.toggle(this);
            return true;
        } else if (id == R.id.action_reset) {
            confirmReset();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------- upload (feature d)

    private void uploadAll() {
        if (uploading) return;

        if (!CloudConfig.isConfigured()) {
            showMessage(getString(R.string.upload_failed_title), getString(R.string.cloud_not_configured));
            return;
        }
        if (!uploader.isOnline()) {
            showMessage(getString(R.string.upload_failed_title), getString(R.string.no_internet));
            return;
        }

        setUploading(true);
        Toast.makeText(this, R.string.uploading, Toast.LENGTH_SHORT).show();

        uploader.uploadAll(new CloudUploader.UploadCallback() {
            @Override
            public void onSuccess(int sessionCount, int observationCount) {
                setUploading(false);
                loadSessions(); // shows the green "uploaded" cloud icons
                showMessage(getString(R.string.upload_success_title),
                        getString(R.string.upload_success_message, sessionCount, observationCount));
            }

            @Override
            public void onError(String message) {
                setUploading(false);
                showMessage(getString(R.string.upload_failed_title), message);
            }
        });
    }

    private void setUploading(boolean value) {
        uploading = value;
        progressUpload.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String title, String message) {
        if (isFinishing()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    // ---------------------------------------------------------------- reset

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_delete, (d, w) -> {
                    db.deleteAllSessions();
                    deleteAllPhotos();
                    loadSessions();
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void deleteAllPhotos() {
        File[] files = new File(getFilesDir(), "photos").listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    // ---------------------------------------------------------------- list callbacks

    @Override
    public void onSessionClick(ObservingSession session) {
        Intent intent = new Intent(this, SessionDetailActivity.class);
        intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, session.getId());
        startActivity(intent);
    }

    @Override
    public void onEditSession(ObservingSession session) {
        Intent intent = new Intent(this, SessionFormActivity.class);
        intent.putExtra(SessionFormActivity.EXTRA_SESSION_ID, session.getId());
        startActivity(intent);
    }

    @Override
    public void onDeleteSession(ObservingSession session) {
        SessionActions.confirmDelete(this, session, this::loadSessions);
    }
}
