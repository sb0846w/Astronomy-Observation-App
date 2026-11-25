namespace AstroExplorer.Services;

/// <summary>
/// Stores user settings on the device with MAUI Preferences (simple key/value persistence):
/// the cloud database URL, the chosen theme and the list of favourite objects.
/// </summary>
public class SettingsService
{
    /// <summary>
    /// Supabase project URL (same as CloudConfig.java in the Android app).
    /// It can also be changed at run time on the Settings tab.
    /// </summary>
    public const string DefaultDatabaseUrl = "https://rlauvxzzjpdkbxwcfogj.supabase.co";

    /// <summary>
    /// Supabase publishable (public) key – the same one as in the Android app's CloudConfig.java.
    /// It is public by design: Row Level Security only lets it READ sessions and observations.
    /// Never put the secret / service_role key in the app or in Git.
    /// </summary>
    public const string PublishableKey ="sb_publishable_7IfKZ7q4v_0hodY0CGxeKg_9S2KrFA1";

    private const string KeyUrl = "database_url";
    private const string KeyTheme = "theme";
    private const string KeyFavourites = "favourites";
    private const char Separator = '|';

    public string DatabaseUrl
    {
        get => Preferences.Default.Get(KeyUrl, DefaultDatabaseUrl);
        set => Preferences.Default.Set(KeyUrl, value.Trim().TrimEnd('/'));
    }

    public bool IsDatabaseConfigured =>
        DatabaseUrl.StartsWith("https://", StringComparison.OrdinalIgnoreCase) && !DatabaseUrl.Contains("YOUR-PROJECT");

    /// <summary>0 = follow system, 1 = light, 2 = dark.</summary>
    public int ThemeChoice
    {
        get => Preferences.Default.Get(KeyTheme, 2); // dark by default: the app is used at night
        set => Preferences.Default.Set(KeyTheme, value);
    }

    public void ApplyTheme()
    {
        if (Application.Current is null) return;
        Application.Current.UserAppTheme = ThemeChoice switch
        {
            1 => AppTheme.Light,
            2 => AppTheme.Dark,
            _ => AppTheme.Unspecified
        };
    }

    // ---- Favourites (feature f) ----

    public HashSet<string> GetFavourites()
    {
        string raw = Preferences.Default.Get(KeyFavourites, "");
        return raw.Split(Separator, StringSplitOptions.RemoveEmptyEntries).ToHashSet();
    }

    public bool IsFavourite(string name) => GetFavourites().Contains(name);

    /// <summary>Adds or removes a favourite and returns the new state.</summary>
    public bool ToggleFavourite(string name)
    {
        var favs = GetFavourites();
        bool nowFavourite;
        if (favs.Contains(name))
        {
            favs.Remove(name);
            nowFavourite = false;
        }
        else
        {
            favs.Add(name);
            nowFavourite = true;
        }
        Preferences.Default.Set(KeyFavourites, string.Join(Separator, favs));
        return nowFavourite;
    }
}
