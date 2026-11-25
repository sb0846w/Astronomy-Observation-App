using System.Text.Json.Serialization;

namespace AstroExplorer.Models;

/// <summary>
/// One celestial object seen during a session (Supabase table celestial_observations).
/// [JsonPropertyName] maps our C# names to the database column names.
/// </summary>
public class CelestialObservation
{
    public string Id { get; set; } = "";                                                      // UUID
    [JsonPropertyName("celestial_object")]    public string ObjectName { get; set; } = "";
    public string? ObjectType { get; set; }
    [JsonPropertyName("location_in_sky")]     public string SkyLocation { get; set; } = "";
    [JsonPropertyName("observation_details")] public string Details { get; set; } = "";
    [JsonPropertyName("additional_comments")] public string? Comments { get; set; }
    public string? ObservedTime { get; set; }
    public bool HasPhoto { get; set; }

    /// <summary>
    /// Back-reference to the session this observation belongs to.
    /// Not part of the JSON – filled in by CloudService after downloading.
    /// </summary>
    [JsonIgnore]
    public ObservingSession Session { get; set; } = new();

    // ---- Display helpers ----
    public string TypeAndLocation =>
        string.IsNullOrWhiteSpace(ObjectType) ? SkyLocation : $"{ObjectType}  •  {SkyLocation}";

    public string SessionSummary => $"{Session.Name}  •  {Session.DisplayDate}  •  {Session.ObserverDisplay}";

    public bool HasComments => !string.IsNullOrWhiteSpace(Comments);

    public string TimeDisplay => string.IsNullOrWhiteSpace(ObservedTime) ? Session.Time : ObservedTime!;
}
