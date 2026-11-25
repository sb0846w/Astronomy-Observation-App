using System.Globalization;
using System.Text.Json.Serialization;

namespace AstroExplorer.Models;

/// <summary>
/// An observing session uploaded by the Android app (Supabase table observing_sessions).
/// Properties map to snake_case columns automatically (DurationHours = duration_hours);
/// [JsonPropertyName] is used where our C# name differs from the column name.
/// </summary>
public class ObservingSession
{
    [JsonPropertyName("id")]                 public string CloudId { get; set; } = "";   // UUID
    [JsonPropertyName("session_title")]      public string Name { get; set; } = "";
    [JsonPropertyName("session_date")]       public string Date { get; set; } = "";      // yyyy-MM-dd
    [JsonPropertyName("session_time")]       public string Time { get; set; } = "";      // HH:mm
    [JsonPropertyName("observing_location")] public string Location { get; set; } = "";
    public double DurationHours { get; set; }
    public string? Description { get; set; }
    public string? SkyConditions { get; set; }
    public string? ObserverName { get; set; }
    public string? Equipment { get; set; }
    public int? BortleScale { get; set; }           // optional in the database
    public int? SeeingRating { get; set; }
    public double? Latitude { get; set; }
    public double? Longitude { get; set; }
    public DateTimeOffset? SyncedAt { get; set; }    // last upload from the phone

    /// <summary>Embedded by the REST query  select=*,celestial_observations(*)</summary>
    [JsonPropertyName("celestial_observations")]
    public List<CelestialObservation>? Observations { get; set; }

    // ---- Display helpers used by the XAML pages ----

    public string DisplayDate =>
        DateTime.TryParseExact(Date, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out var d)
            ? d.ToString("ddd dd MMM yyyy", CultureInfo.GetCultureInfo("en-GB"))
            : Date;

    public string ObserverDisplay => string.IsNullOrWhiteSpace(ObserverName) ? "Anonymous observer" : ObserverName!;

    public string SkyDisplay => string.IsNullOrWhiteSpace(SkyConditions) ? "Not recorded" : SkyConditions!;

    public string EquipmentDisplay => string.IsNullOrWhiteSpace(Equipment) ? "Not recorded" : Equipment!;

    public bool HasDescription => !string.IsNullOrWhiteSpace(Description);

    public bool HasCoordinates => Latitude.HasValue && Longitude.HasValue;

    public string SeeingStars
    {
        get
        {
            int stars = Math.Clamp(SeeingRating ?? 0, 0, 5);
            return new string('★', stars) + new string('☆', 5 - stars);
        }
    }
}
