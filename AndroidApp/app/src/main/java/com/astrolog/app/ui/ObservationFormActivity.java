package com.astrolog.app.ui;

import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.astrolog.app.R;
import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.CelestialObservation;
import com.astrolog.app.util.DateUtils;
import com.astrolog.app.util.PhotoUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Objects;

/**
 * Add or edit one celestial object observation for a session (feature c),
 * optionally with a photo from the camera or gallery (feature f).
 */
public class ObservationFormActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_OBSERVATION_ID = "observation_id";

    private static final String STATE_PHOTO = "photo_path";
    private static final String STATE_PENDING_CAMERA = "pending_camera";

    private AstroDbHelper db;
    private long sessionId;
    private CelestialObservation editing;     // null when adding

    private TextInputLayout layoutObjectName, layoutSkyLocation, layoutDetails;
    private MaterialAutoCompleteTextView editObjectName, dropdownObjectType, editSkyLocation;
    private TextInputEditText editObservedTime, editDetails, editComments;
    private ImageView imagePhoto;
    private View buttonRemovePhoto;

    private String photoPath;          // photo currently shown in the form
    private String originalPhotoPath;  // photo stored in the DB when we opened the form
    private String pendingCameraPath;  // file the camera is writing to right now
    private boolean saved = false;

    // Take a picture with the camera app, written straight into our private file
    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraPath != null) {
                    replacePhoto(pendingCameraPath);
                } else {
                    PhotoUtils.deleteQuietly(pendingCameraPath); // cancelled – remove the empty file
                }
                pendingCameraPath = null;
            });

    // Pick an image with the system photo picker (no storage permission needed)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                try {
                    replacePhoto(PhotoUtils.copyToAppStorage(this, uri));
                } catch (IOException e) {
                    Toast.makeText(this, R.string.photo_error, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_observation_form);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = AstroDbHelper.getInstance(this);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, 0);
        long observationId = getIntent().getLongExtra(EXTRA_OBSERVATION_ID, 0);
        if (observationId > 0) editing = db.getObservation(observationId);

        bindViews();
        setUpSuggestions();
        setUpTimePicker();
        setUpPhotoButtons();

        if (editing != null) {
            setTitle(R.string.title_edit_observation);
            originalPhotoPath = editing.getPhotoPath();
            if (savedInstanceState == null) {
                populate(editing);
                photoPath = originalPhotoPath;
            }
        } else {
            setTitle(R.string.title_add_observation);
            if (savedInstanceState == null) {
                // Sensible default: the object was seen just now
                Calendar now = Calendar.getInstance();
                editObservedTime.setText(DateUtils.formatTime(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)));
            }
        }

        if (savedInstanceState != null) {
            photoPath = savedInstanceState.getString(STATE_PHOTO);
            pendingCameraPath = savedInstanceState.getString(STATE_PENDING_CAMERA);
        }
        showPhoto();

        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PHOTO, photoPath);
        outState.putString(STATE_PENDING_CAMERA, pendingCameraPath);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Leaving without saving: tidy up any new photo that was never stored
        if (isFinishing() && !saved && photoPath != null && !photoPath.equals(originalPhotoPath)) {
            PhotoUtils.deleteQuietly(photoPath);
        }
    }

    private void bindViews() {
        layoutObjectName = findViewById(R.id.layoutObjectName);
        layoutSkyLocation = findViewById(R.id.layoutSkyLocation);
        layoutDetails = findViewById(R.id.layoutDetails);
        editObjectName = findViewById(R.id.editObjectName);
        dropdownObjectType = findViewById(R.id.dropdownObjectType);
        editSkyLocation = findViewById(R.id.editSkyLocation);
        editObservedTime = findViewById(R.id.editObservedTime);
        editDetails = findViewById(R.id.editDetails);
        editComments = findViewById(R.id.editComments);
        imagePhoto = findViewById(R.id.imagePhoto);
        buttonRemovePhoto = findViewById(R.id.buttonRemovePhoto);

        clearErrorWhenEdited(layoutObjectName, editObjectName);
        clearErrorWhenEdited(layoutSkyLocation, editSkyLocation);
        clearErrorWhenEdited(layoutDetails, editDetails);
    }

    /** Auto-complete suggestions for object names and sky locations – less typing for the user. */
    private void setUpSuggestions() {
        String[] objects = getResources().getStringArray(R.array.common_objects);
        String[] objectTypes = getResources().getStringArray(R.array.common_object_types);
        editObjectName.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, objects));

        // Picking a well-known object also fills in its type automatically
        editObjectName.setOnItemClickListener((parent, view, position, id) -> {
            String chosen = (String) parent.getItemAtPosition(position);
            for (int i = 0; i < objects.length; i++) {
                if (objects[i].equals(chosen)) {
                    dropdownObjectType.setText(objectTypes[i], false);
                    break;
                }
            }
        });

        String[] skyLocations = getResources().getStringArray(R.array.sky_locations);
        editSkyLocation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, skyLocations));
    }

    private void setUpTimePicker() {
        editObservedTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE);
            String current = textOf(editObservedTime);
            if (current.matches("\\d{2}:\\d{2}")) {
                hour = Integer.parseInt(current.substring(0, 2));
                minute = Integer.parseInt(current.substring(3, 5));
            }
            new TimePickerDialog(this, (p, h, m) -> editObservedTime.setText(DateUtils.formatTime(h, m)),
                    hour, minute, true).show();
        });
    }

    private void populate(CelestialObservation o) {
        editObjectName.setText(o.getObjectName(), false);
        if (!TextUtils.isEmpty(o.getObjectType())) dropdownObjectType.setText(o.getObjectType(), false);
        editSkyLocation.setText(o.getSkyLocation(), false);
        editObservedTime.setText(o.getObservedTime());
        editDetails.setText(o.getDetails());
        editComments.setText(o.getComments());
    }

    // ---------------------------------------------------------------- photo (feature f)

    private void setUpPhotoButtons() {
        findViewById(R.id.buttonCamera).setOnClickListener(v -> {
            File file = PhotoUtils.createPhotoFile(this);
            pendingCameraPath = file.getAbsolutePath();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            try {
                takePictureLauncher.launch(uri);
            } catch (android.content.ActivityNotFoundException e) {
                PhotoUtils.deleteQuietly(pendingCameraPath);
                pendingCameraPath = null;
                Toast.makeText(this, "No camera app available", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.buttonGallery).setOnClickListener(v ->
                pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));

        buttonRemovePhoto.setOnClickListener(v -> replacePhoto(null));
    }

    /** Swaps the current photo, deleting the previous one if it was only added in this form. */
    private void replacePhoto(String newPath) {
        if (photoPath != null && !photoPath.equals(originalPhotoPath)) {
            PhotoUtils.deleteQuietly(photoPath);
        }
        photoPath = newPath;
        showPhoto();
    }

    private void showPhoto() {
        Bitmap bitmap = PhotoUtils.loadScaled(photoPath, 1024);
        if (bitmap != null) {
            imagePhoto.setImageBitmap(bitmap);
            imagePhoto.setVisibility(View.VISIBLE);
            buttonRemovePhoto.setVisibility(View.VISIBLE);
        } else {
            imagePhoto.setVisibility(View.GONE);
            buttonRemovePhoto.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- save / delete

    private void onSaveClicked() {
        boolean ok = require(layoutObjectName, editObjectName);
        ok &= require(layoutSkyLocation, editSkyLocation);
        ok &= require(layoutDetails, editDetails);
        if (!ok) {
            Toast.makeText(this, R.string.error_fix_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        CelestialObservation o = editing != null ? editing : new CelestialObservation();
        o.setSessionId(sessionId);
        o.setObjectName(textOf(editObjectName));
        o.setObjectType(emptyToNull(textOf(dropdownObjectType)));
        o.setSkyLocation(textOf(editSkyLocation));
        o.setObservedTime(emptyToNull(textOf(editObservedTime)));
        o.setDetails(textOf(editDetails));
        o.setComments(emptyToNull(textOf(editComments)));
        o.setPhotoPath(photoPath);

        if (editing != null) {
            db.updateObservation(o);
        } else {
            db.insertObservation(o);
        }

        // The old photo was replaced or removed – delete the old file now that the change is saved
        if (originalPhotoPath != null && !Objects.equals(originalPhotoPath, photoPath)) {
            PhotoUtils.deleteQuietly(originalPhotoPath);
        }
        saved = true;
        Toast.makeText(this, R.string.observation_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_observation_title)
                .setMessage(getString(R.string.delete_observation_message, editing.getObjectName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.menu_delete, (d, w) -> {
                    db.deleteObservation(editing);
                    PhotoUtils.deleteQuietly(originalPhotoPath);
                    if (photoPath != null && !photoPath.equals(originalPhotoPath)) PhotoUtils.deleteQuietly(photoPath);
                    saved = true; // nothing left to clean up in onDestroy
                    finish();
                })
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (editing != null) { // delete only makes sense for an existing observation
            getMenuInflater().inflate(R.menu.menu_observation_form, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ---------------------------------------------------------------- helpers

    private boolean require(TextInputLayout layout, EditText edit) {
        if (textOf(edit).isEmpty()) {
            layout.setError(getString(R.string.error_required));
            return false;
        }
        layout.setError(null);
        return true;
    }

    private static void clearErrorWhenEdited(TextInputLayout layout, EditText edit) {
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) layout.setError(null);
            }
        });
    }

    private static String textOf(EditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private static String emptyToNull(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }
}
