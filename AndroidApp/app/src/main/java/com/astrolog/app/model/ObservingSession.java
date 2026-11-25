package com.astrolog.app.model;

import java.util.UUID;

/**
 * One astronomy observing session (feature a).
 * Plain data class: the database helper fills it from / writes it to SQLite.
 */
public class ObservingSession {

    // ---- Database / sync fields ----
    private long id;                 // local SQLite primary key (0 = not saved yet)
    private String cloudId;          // stable id used as the key in the cloud database
    private Long uploadedAt;         // time of last upload in millis, null if never uploaded

    // ---- Required fields ----
    private String name;
    private String date;             // stored as yyyy-MM-dd so it sorts correctly
    private String time;             // stored as HH:mm (24h)
    private String location;
    private double durationHours;

    // ---- Optional fields ----
    private String description;
    private String skyConditions;

    // ---- Our own extra fields ----
    private String observerName;     // who made the observations (shown in hybrid app)
    private String equipment;        // Naked eye / Binoculars / Telescope
    private int bortleScale;         // 1 (darkest sky) .. 9 (inner city sky)
    private int seeingRating;        // 1..5 stars, how steady the atmosphere was
    private Double latitude;         // filled automatically from GPS, may be null
    private Double longitude;

    // Not stored in the sessions table – calculated by a COUNT query for the list screen
    private int observationCount;

    public ObservingSession() {
        // New sessions get a random cloud id so re-uploading overwrites instead of duplicating
        this.cloudId = UUID.randomUUID().toString();
        this.bortleScale = 5;
        this.seeingRating = 3;
    }

    /** Human readable duration, e.g. "1.5 h". */
    public String getDurationLabel() {
        if (durationHours == Math.floor(durationHours)) {
            return ((int) durationHours) + " h";
        }
        return durationHours + " h";
    }

    public boolean isUploaded() {
        return uploadedAt != null;
    }

    // ---- Getters and setters ----
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public Long getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Long uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getDurationHours() { return durationHours; }
    public void setDurationHours(double durationHours) { this.durationHours = durationHours; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSkyConditions() { return skyConditions; }
    public void setSkyConditions(String skyConditions) { this.skyConditions = skyConditions; }

    public String getObserverName() { return observerName; }
    public void setObserverName(String observerName) { this.observerName = observerName; }

    public String getEquipment() { return equipment; }
    public void setEquipment(String equipment) { this.equipment = equipment; }

    public int getBortleScale() { return bortleScale; }
    public void setBortleScale(int bortleScale) { this.bortleScale = bortleScale; }

    public int getSeeingRating() { return seeingRating; }
    public void setSeeingRating(int seeingRating) { this.seeingRating = seeingRating; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public int getObservationCount() { return observationCount; }
    public void setObservationCount(int observationCount) { this.observationCount = observationCount; }
}
