namespace AstroExplorer.Models;

/// <summary>Reference information about a celestial object, shown in the Explore tab.</summary>
public class CelestialObjectInfo
{
    public string Name { get; init; } = "";
    public string Type { get; init; } = "";
    public string Symbol { get; init; } = "★";           // simple glyph shown in lists
    public string[] Aliases { get; init; } = [];           // other names, e.g. "M42"
    public string Constellation { get; init; } = "";
    public string Distance { get; init; } = "";
    public string Brightness { get; init; } = "";
    public string BestSeen { get; init; } = "";
    public string Description { get; init; } = "";
    public string ObservingTips { get; init; } = "";

    // Set at runtime by the Explore page
    public int ObservationCount { get; set; }
    public bool IsFavourite { get; set; }

    public string CountLabel => ObservationCount switch
    {
        0 => "No observations yet",
        1 => "1 observation",
        _ => $"{ObservationCount} observations"
    };

    public string FavouriteGlyph => IsFavourite ? "♥" : "";

    /// <summary>True if an observation's object name refers to this object (by name or alias).</summary>
    public bool Matches(string objectName)
    {
        if (string.IsNullOrWhiteSpace(objectName)) return false;
        if (objectName.Contains(Name, StringComparison.OrdinalIgnoreCase)) return true;
        return Aliases.Any(a => objectName.Contains(a, StringComparison.OrdinalIgnoreCase));
    }
}
