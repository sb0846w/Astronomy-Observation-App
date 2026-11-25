package com.astrolog.app.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.astrolog.app.R;
import com.astrolog.app.data.AstroDbHelper;
import com.astrolog.app.model.ObservingSession;
import com.astrolog.app.util.DateUtils;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.widget.RatingBar;
import android.widget.TextView;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Add or edit an observing session (feature a).
 *
 * Flow: user fills in the form → validate required fields (error shown on each missing field)
 *       → confirmation dialog shows all details → "Go back & edit" or "Confirm & save"
 *       → saved to SQLite (feature b).
 *
 * If EXTRA_SESSION_ID is supplied the form opens in edit mode with the existing values.
 */
public class SessionFormActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    private static final String STATE_LAT = "lat";
    private static final String STATE_LNG = "lng";

    private AstroDbHelper db;
    private ObservingSession editingSession; // null when adding a new session

    private TextInputLayout layoutName, layoutDate, layoutTime, layoutLocation, layoutDuration;
    private TextInputEditText editName, editDate, editTime, editLocation, editDuration, editDescription, editObserver;
    private MaterialAutoCompleteTextView dropdownSky;
    private ChipGroup chipGroupEquipment;
    private Slider sliderBortle;
    private TextView textBortle;
    private RatingBar ratingSeeing;

    // Coordinates captured from GPS (null if the location was typed)
    private Double latitude, longitude;

    private final ExecutorService background = Executors.newSingleThreadExecutor();

    /** Asks for location permission; if granted we fetch the location straight away. */
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    fetchLocation();
                } else {
                    Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_form);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        db = AstroDbHelper.getInstance(this);
        bindViews();
        setUpPickers();
        setUpExtras();

        long sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, 0);
        if (sessionId > 0) {
            editingSession = db.getSession(sessionId);
        }

        if (editingSession != null) {
            setTitle(R.string.title_edit_session);
            if (savedInstanceState == null) populateForm(editingSession);
        } else {
            setTitle(R.string.title_add_session);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_LAT)) {
            latitude = savedInstanceState.getDouble(STATE_LAT);
            longitude = savedInstanceState.getDouble(STATE_LNG);
        }

        findViewById(R.id.buttonSave).setOnClickListener(v -> onSaveClicked());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Text fields restore themselves; we only need to keep the GPS coordinates across rotation
        if (latitude != null && longitude != null) {
            outState.putDouble(STATE_LAT, latitude);
            outState.putDouble(STATE_LNG, longitude);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        background.shutdownNow();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void bindViews() {
        layoutName = findViewById(R.id.layoutName);
        layoutDate = findViewById(R.id.layoutDate);
        layoutTime = findViewById(R.id.layoutTime);
        layoutLocation = findViewById(R.id.layoutLocation);
        layoutDuration = findViewById(R.id.layoutDuration);

        editName = findViewById(R.id.editName);
        editDate = findViewById(R.id.editDate);
        editTime = findViewById(R.id.editTime);
        editLocation = findViewById(R.id.editLocation);
        editDuration = findViewById(R.id.editDuration);
        editDescription = findViewById(R.id.editDescription);
        editObserver = findViewById(R.id.editObserver);
        dropdownSky = findViewById(R.id.dropdownSky);
        chipGroupEquipment = findViewById(R.id.chipGroupEquipment);
        sliderBortle = findViewById(R.id.sliderBortle);
        textBortle = findViewById(R.id.textBortle);
        ratingSeeing = findViewById(R.id.ratingSeeing);

        // Remove an error message as soon as the user starts fixing that field
        clearErrorWhenEdited(layoutName, editName);
        clearErrorWhenEdited(layoutDate, editDate);
        clearErrorWhenEdited(layoutTime, editTime);
        clearErrorWhenEdited(layoutLocation, editLocation);
        clearErrorWhenEdited(layoutDuration, editDuration);
    }

    // ---------------------------------------------------------------- pickers

    /** Date and time are chosen with dialogs instead of typed, so the format is always valid. */
    private void setUpPickers() {
        editDate.setOnClickListener(v -> {
            Calendar cal = DateUtils.parse(storedDate());
            new DatePickerDialog(this, (picker, year, month, day) -> {
                Calendar chosen = Calendar.getInstance();
                chosen.set(year, month, day);
                editDate.setTag(DateUtils.toStorage(chosen));          // value saved to the DB
                editDate.setText(DateUtils.toDisplay(DateUtils.toStorage(chosen))); // friendly text
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });

        editTime.setOnClickListener(v -> {
            int hour = 21, minute = 0; // sensible default: 9pm, after dark
            String current = textOf(editTime);
            if (current.matches("\\d{2}:\\d{2}")) {
                hour = Integer.parseInt(current.substring(0, 2));
                minute = Integer.parseInt(current.substring(3, 5));
            }
            new TimePickerDialog(this, (picker, h, m) -> editTime.setText(DateUtils.formatTime(h, m)),
                    hour, minute, true).show();
        });
    }

    /** The date field shows "Fri 05 Dec 2026" but we store "2026-12-05" in its tag. */
    private String storedDate() {
        Object tag = editDate.getTag();
        if (tag != null) return tag.toString();
        // After rotation the tag is lost but the display text remains – convert it back
        String shown = textOf(editDate);
        if (shown.isEmpty()) return null;
        try {
            java.util.Date d = new java.text.SimpleDateFormat("EEE dd MMM yyyy", Locale.UK).parse(shown);
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                return DateUtils.toStorage(c);
            }
        } catch (java.text.ParseException ignored) {
            // fall through
        }
        return null;
    }

    private void setUpExtras() {
        sliderBortle.addOnChangeListener((slider, value, fromUser) -> updateBortleLabel((int) value));
        updateBortleLabel((int) sliderBortle.getValue());

        // GPS button inside the location field (feature f)
        layoutLocation.setEndIconOnClickListener(v -> requestLocation());
    }

    private void updateBortleLabel(int value) {
        textBortle.setText(getString(R.string.label_bortle, value));
    }

    private void populateForm(ObservingSession s) {
        editName.setText(s.getName());
        editDate.setTag(s.getDate());
        editDate.setText(DateUtils.toDisplay(s.getDate()));
        editTime.setText(s.getTime());
        editLocation.setText(s.getLocation());
        editDuration.setText(formatNumber(s.getDurationHours()));
        editDescription.setText(s.getDescription());
        editObserver.setText(s.getObserverName());
        if (!TextUtils.isEmpty(s.getSkyConditions())) {
            dropdownSky.setText(s.getSkyConditions(), false); // false = don't filter the list
        }
        sliderBortle.setValue(clamp(s.getBortleScale(), 1, 9));
        ratingSeeing.setRating(clamp(s.getSeeingRating(), 1, 5));
        checkEquipmentChip(s.getEquipment());
        latitude = s.getLatitude();
        longitude = s.getLongitude();
    }

    private void checkEquipmentChip(String equipment) {
        if (getString(R.string.equip_binoculars).equals(equipment)) {
            chipGroupEquipment.check(R.id.chipBinoculars);
        } else if (getString(R.string.equip_telescope).equals(equipment)) {
            chipGroupEquipment.check(R.id.chipTelescope);
        } else {
            chipGroupEquipment.check(R.id.chipNakedEye);
        }
    }

    private String selectedEquipment() {
        int id = chipGroupEquipment.getCheckedChipId();
        if (id == R.id.chipBinoculars) return getString(R.string.equip_binoculars);
        if (id == R.id.chipTelescope) return getString(R.string.equip_telescope);
        return getString(R.string.equip_naked_eye);
    }

    // ---------------------------------------------------------------- validation & save

    private void onSaveClicked() {
        if (!validate()) {
            Toast.makeText(this, R.string.error_fix_fields, Toast.LENGTH_SHORT).show();
            return;
        }
        ObservingSession session = buildSessionFromForm();
        showConfirmation(session);
    }

    /**
     * Checks every required field. Each missing field gets its own error message
     * so the user can see exactly what to fix. Returns true when everything is valid.
     */
    private boolean validate() {
        boolean ok = true;
        ok &= requireText(layoutName, editName);
        ok &= requireText(layoutDate, editDate);
        ok &= requireText(layoutTime, editTime);
        ok &= requireText(layoutLocation, editLocation);

        String durationText = textOf(editDuration);
        if (durationText.isEmpty()) {
            layoutDuration.setError(getString(R.string.error_required));
            ok = false;
        } else {
            double hours = parseDouble(durationText);
            if (hours < 0.1 || hours > 24) {
                layoutDuration.setError(getString(R.string.error_duration));
                ok = false;
            }
        }
        return ok;
    }

    private boolean requireText(TextInputLayout layout, TextInputEditText edit) {
        if (textOf(edit).isEmpty()) {
            layout.setError(getString(R.string.error_required));
            return false;
        }
        layout.setError(null);
        return true;
    }

    private ObservingSession buildSessionFromForm() {
        // Start from the existing session when editing so we keep its id and cloudId
        ObservingSession s = editingSession != null ? editingSession : new ObservingSession();
        s.setName(textOf(editName));
        s.setDate(storedDate());
        s.setTime(textOf(editTime));
        s.setLocation(textOf(editLocation));
        s.setDurationHours(parseDouble(textOf(editDuration)));
        s.setDescription(emptyToNull(textOf(editDescription)));
        s.setSkyConditions(emptyToNull(dropdownSky.getText() == null ? "" : dropdownSky.getText().toString().trim()));
        s.setObserverName(emptyToNull(textOf(editObserver)));
        s.setEquipment(selectedEquipment());
        s.setBortleScale((int) sliderBortle.getValue());
        s.setSeeingRating(Math.max(1, (int) ratingSeeing.getRating()));
        s.setLatitude(latitude);
        s.setLongitude(longitude);
        return s;
    }

    /** Shows all entered details back to the user before anything is saved (feature a). */
    private void showConfirmation(ObservingSession s) {
        StringBuilder sb = new StringBuilder();
        line(sb, "Name", s.getName());
        line(sb, "Date", DateUtils.toDisplay(s.getDate()));
        line(sb, "Time", s.getTime());
        line(sb, "Location", s.getLocation());
        line(sb, "Duration", s.getDurationLabel());
        line(sb, "Sky conditions", s.getSkyConditions());
        line(sb, "Description", s.getDescription());
        line(sb, "Observer", s.getObserverName());
        line(sb, "Equipment", s.getEquipment());
        line(sb, "Bortle scale", String.valueOf(s.getBortleScale()));
        line(sb, "Seeing", s.getSeeingRating() + " / 5");
        if (s.getLatitude() != null) {
            line(sb, "GPS", String.format(Locale.UK, "%.4f, %.4f", s.getLatitude(), s.getLongitude()));
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(sb.toString().trim())
                .setNegativeButton(R.string.confirm_edit, null) // just closes the dialog; values stay in the form
                .setPositiveButton(R.string.confirm_save, (d, w) -> saveSession(s))
                .show();
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(label).append(": ").append(TextUtils.isEmpty(value) ? "—" : value).append('\n');
    }

    private void saveSession(ObservingSession s) {
        if (editingSession != null) {
            db.updateSession(s);
            Toast.makeText(this, R.string.session_updated, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            long id = db.insertSession(s);
            if (id == -1) {
                Toast.makeText(this, "Could not save the session", Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, R.string.session_saved, Toast.LENGTH_SHORT).show();
            // Go straight to the new session so the user can start adding objects
            Intent intent = new Intent(this, SessionDetailActivity.class);
            intent.putExtra(SessionDetailActivity.EXTRA_SESSION_ID, id);
            startActivity(intent);
            finish();
        }
    }

    // ---------------------------------------------------------------- GPS location (feature f)

    private void requestLocation() {
        boolean hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (hasPermission) {
            fetchLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @SuppressLint("MissingPermission") // permission is checked in requestLocation()
    private void fetchLocation() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        String provider;
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            provider = LocationManager.GPS_PROVIDER;
        } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            provider = LocationManager.NETWORK_PROVIDER;
        } else {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        layoutLocation.setHelperText(getString(R.string.locating));
        try {
            // A recent cached fix is instant – use it if it is less than 10 minutes old
            Location last = lm.getLastKnownLocation(provider);
            if (last != null && System.currentTimeMillis() - last.getTime() < 10 * 60 * 1000) {
                onLocationFound(last);
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(this), this::onLocationFound);
            } else {
                LocationListener listener = this::onLocationFound;
                //noinspection deprecation – only used on Android 10 and older
                lm.requestSingleUpdate(provider, listener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            layoutLocation.setHelperText(null);
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_LONG).show();
        }
    }

    private void onLocationFound(Location location) {
        if (isFinishing() || isDestroyed()) return;
        layoutLocation.setHelperText(null);
        if (location == null) {
            Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        String coords = String.format(Locale.UK, "%.4f, %.4f", latitude, longitude);
        editLocation.setText(coords);

        // Turn coordinates into a place name on a background thread (Geocoder may use the network)
        if (!Geocoder.isPresent()) return;
        final double lat = latitude, lng = longitude;
        background.execute(() -> {
            String placeName = reverseGeocode(lat, lng);
            if (placeName != null) {
                runOnUiThread(() -> {
                    if (!isFinishing()) editLocation.setText(placeName);
                });
            }
        });
    }

    @SuppressWarnings("deprecation") // the synchronous call is fine on a background thread
    private String reverseGeocode(double lat, double lng) {
        try {
            List<Address> results = new Geocoder(this, Locale.UK).getFromLocation(lat, lng, 1);
            if (results == null || results.isEmpty()) return null;
            Address a = results.get(0);
            String place = a.getLocality() != null ? a.getLocality() : a.getSubAdminArea();
            String region = a.getAdminArea() != null ? a.getAdminArea() : a.getCountryName();
            if (place == null && region == null) return null;
            if (place == null) return region;
            return region == null ? place : place + ", " + region;
        } catch (IOException | IllegalArgumentException e) {
            return null; // keep showing the coordinates
        }
    }

    // ---------------------------------------------------------------- small helpers

    private static void clearErrorWhenEdited(TextInputLayout layout, TextInputEditText edit) {
        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() > 0) layout.setError(null);
            }
        });
    }

    private static String textOf(TextInputEditText edit) {
        return edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private static String emptyToNull(String s) {
        return TextUtils.isEmpty(s) ? null : s;
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String formatNumber(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
