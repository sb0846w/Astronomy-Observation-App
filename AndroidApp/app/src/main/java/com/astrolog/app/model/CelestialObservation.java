package com.astrolog.app.model;

import java.util.UUID;

/**
 * One celestial object seen during an observing session (feature c).
 * Many observations belong to one {@link ObservingSession} (one-to-many relationship).
 */
public class CelestialObservation {

    private long id;
    private String cloudId;          // stable UUID, primary key of celestial_observations in Supabase
    private long sessionId;          // foreign key -> sessions.id

    // Required fields
    private String objectName;       // e.g. "Jupiter", "Orion Nebula"
    private String skyLocation;      // e.g. "Orion, low in the south-east"
    private String details;          // what it looked like

    // Optional fields
    private String comments;         // equipment used, unusual features...

    // Extra fields of our own
    private String objectType;       // Planet, Galaxy, Nebula...
    private String observedTime;     // HH:mm the object was seen
    private String photoPath;        // absolute path of a photo in app storage, may be null

    public CelestialObservation() {
        // Random UUID so re-uploading updates the same cloud row instead of duplicating it
        this.cloudId = UUID.randomUUID().toString();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getCloudId() { return cloudId; }
    public void setCloudId(String cloudId) { this.cloudId = cloudId; }

    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }

    public String getObjectName() { return objectName; }
    public void setObjectName(String objectName) { this.objectName = objectName; }

    public String getSkyLocation() { return skyLocation; }
    public void setSkyLocation(String skyLocation) { this.skyLocation = skyLocation; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getObservedTime() { return observedTime; }
    public void setObservedTime(String observedTime) { this.observedTime = observedTime; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
}
