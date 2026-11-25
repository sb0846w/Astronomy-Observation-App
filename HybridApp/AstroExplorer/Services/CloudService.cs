using System.Net.Http.Json;
using System.Text.Json;
using AstroExplorer.Models;

namespace AstroExplorer.Services;

/// <summary>Result of trying to load observations, so pages can show a friendly status line.</summary>
public record LoadResult(bool Success, bool FromCache, string Message);

/// <summary>One page of server-side search results (see <see cref="CloudService.SearchCloudAsync"/>).</summary>
public record SearchPage(IReadOnlyList<CelestialObservation> Items, long TotalCount, string? Error)
{
    public bool IsEmpty => Error is null && Items.Count == 0;
}

/// <summary>
/// Downloads the observing sessions uploaded by the Android app (feature e) from Supabase.
///
/// Tables: observing_sessions (1) ──&lt; celestial_observations (many).
/// One REST request per page fetches sessions with their observations embedded:
///   GET {url}/rest/v1/observing_sessions?select=*,celestial_observations(*)&amp;limit=100&amp;offset=0
///
/// The app only READS, so it needs no sign-in: the publishable key (role "anon") may read
/// every session and observation, but Row Level Security stops it from changing anything.
///
/// Offline support (feature f): every successful download is saved to a local JSON file.
/// If the phone is offline or the request fails, the last saved copy is shown instead.
/// </summary>
public class CloudService
{
    /// <summary>Sessions fetched per request when downloading everything.</summary>
    private const int PageSize = 100;
    /// <summary>Safety stop: never download more than this many pages.</summary>
    private const int MaxPages = 50;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,   // database columns are snake_case
        PropertyNameCaseInsensitive = true
    };

    private readonly HttpClient _http = CreateClient();
    private readonly SettingsService _settings;
    // "_v2": the cache written for the first prototype backend used different field names
    private readonly string _cacheFile = Path.Combine(FileSystem.AppDataDirectory, "sessions_cache_v2.json");
    private const string KeyCacheTime = "cache_time";

    /// <summary>Every Supabase request must carry the project's publishable key in the "apikey" header.</summary>
    private static HttpClient CreateClient()
    {
        var client = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        client.DefaultRequestHeaders.Add("apikey", SettingsService.PublishableKey);
        return client;
    }

    private static string RestUrl(string baseUrl, string pathAndQuery) =>
        $"{baseUrl.TrimEnd('/')}/rest/v1/{pathAndQuery}";

    public CloudService(SettingsService settings)
    {
        _settings = settings;
    }

    /// <summary>All sessions, newest first.</summary>
    public IReadOnlyList<ObservingSession> Sessions { get; private set; } = [];

    /// <summary>Every observation from every session, flattened into one list (newest session first).</summary>
    public IReadOnlyList<CelestialObservation> Observations { get; private set; } = [];

    public bool HasLoaded { get; private set; }

    /// <summary>Raised after new data is loaded so other tabs can refresh themselves.</summary>
    public event EventHandler? DataChanged;

    /// <summary>Loads data the first time it is needed; later calls return immediately.</summary>
    public async Task<LoadResult?> EnsureLoadedAsync()
    {
        if (HasLoaded) return null;
        return await RefreshAsync();
    }

    /// <summary>Fetches the latest data from the cloud, falling back to the local cache.</summary>
    public async Task<LoadResult> RefreshAsync()
    {
        if (!_settings.IsDatabaseConfigured)
        {
            return await LoadFromCacheAsync("Cloud database URL not set – open Settings to add it.");
        }

        if (Connectivity.Current.NetworkAccess != NetworkAccess.Internet)
        {
            return await LoadFromCacheAsync("You are offline.");
        }

        try
        {
            string json = await DownloadAllSessionsAsync();

            SetData(Parse(json));
            await File.WriteAllTextAsync(_cacheFile, json);
            Preferences.Default.Set(KeyCacheTime, DateTime.Now.ToString("g"));

            return new LoadResult(true, false,
                $"{Observations.Count} observations from {Sessions.Count} sessions • updated just now");
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException or IOException)
        {
            return await LoadFromCacheAsync("Could not reach the cloud service.");
        }
    }

    /// <summary>
    /// Downloads every session (with embedded observations) page by page, so a large database
    /// never has to come back in one huge response. Returns one JSON array for the offline cache.
    /// </summary>
    private async Task<string> DownloadAllSessionsAsync()
    {
        var all = new List<JsonElement>();
        for (int page = 0; page < MaxPages; page++)
        {
            string url = RestUrl(_settings.DatabaseUrl,
                "observing_sessions?select=*,celestial_observations(*)" +
                "&celestial_observations.order=sort_order" +          // phone's order inside a session
                "&order=session_date.desc,session_time.desc,id" +       // stable order for paging
                $"&limit={PageSize}&offset={page * PageSize}");

            string json = await _http.GetStringAsync(url);
            using var doc = JsonDocument.Parse(json);
            if (doc.RootElement.ValueKind != JsonValueKind.Array)
                throw new JsonException("Unexpected reply from the cloud service.");

            foreach (var session in doc.RootElement.EnumerateArray())
                all.Add(session.Clone());   // Clone: the element must outlive this JsonDocument

            if (doc.RootElement.GetArrayLength() < PageSize) break;   // last page
        }
        return JsonSerializer.Serialize(all);
    }

    /// <summary>Tests a database URL without changing any data (used by the Settings page).</summary>
    public async Task<string> TestConnectionAsync(string baseUrl)
    {
        try
        {
            // Ask for one id only, plus the total count in the Content-Range header ("0-0/12")
            using var request = new HttpRequestMessage(HttpMethod.Get, RestUrl(baseUrl, "observing_sessions?select=id&limit=1"));
            request.Headers.Add("Prefer", "count=exact");
            using var response = await _http.SendAsync(request);
            if (!response.IsSuccessStatusCode)
            {
                return $"Connection failed: {await ReadErrorAsync(response)}";
            }

            string? range = response.Content.Headers.TryGetValues("Content-Range", out var values)
                ? values.FirstOrDefault() : null;
            string total = range?.Split('/').LastOrDefault() ?? "?";
            return $"Connected ✓  {total} session(s) found in the cloud.";
        }
        catch (Exception ex)
        {
            return $"Connection failed: {ex.Message}";
        }
    }

    /// <summary>
    /// Server-side search through the search_observations database function: case-insensitive,
    /// partial matching on the object name ("jup" finds "Jupiter"), exact matches first, newest first.
    /// Use <paramref name="offset"/> for pagination; TotalCount tells you how many matches exist.
    /// Needs a connection – the Observations tab's offline search uses <see cref="Search"/> instead.
    /// </summary>
    public async Task<SearchPage> SearchCloudAsync(string query, int limit = 20, int offset = 0)
    {
        if (string.IsNullOrWhiteSpace(query))
            return new SearchPage([], 0, "Type the name of an object to search for.");
        if (Connectivity.Current.NetworkAccess != NetworkAccess.Internet)
            return new SearchPage([], 0, "You are offline.");

        try
        {
            var body = new { p_query = query.Trim(), p_limit = limit, p_offset = offset };
            using var response = await _http.PostAsJsonAsync(
                RestUrl(_settings.DatabaseUrl, "rpc/search_observations"), body);
            if (!response.IsSuccessStatusCode)
                return new SearchPage([], 0, await ReadErrorAsync(response));

            var rows = await response.Content.ReadFromJsonAsync<List<SearchRow>>(JsonOptions) ?? [];
            var items = rows.Select(r => r.ToObservation()).ToList();
            return new SearchPage(items, rows.FirstOrDefault()?.TotalCount ?? 0, null);
        }
        catch (Exception ex) when (ex is HttpRequestException or TaskCanceledException or JsonException)
        {
            return new SearchPage([], 0, "Could not reach the cloud service.");
        }
    }

    /// <summary>Turns a Supabase error reply ({"code","message","details"}) into one readable line.</summary>
    private static async Task<string> ReadErrorAsync(HttpResponseMessage response)
    {
        try
        {
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            var root = doc.RootElement;
            string? details = root.TryGetProperty("details", out var d) && d.ValueKind == JsonValueKind.String ? d.GetString() : null;
            string? message = root.TryGetProperty("message", out var m) && m.ValueKind == JsonValueKind.String ? m.GetString() : null;
            return $"{details ?? message ?? "Unknown error"} (HTTP {(int)response.StatusCode})";
        }
        catch (JsonException)
        {
            return $"The server replied with HTTP {(int)response.StatusCode}.";
        }
    }

    /// <summary>One row returned by search_observations: an observation plus its session details.</summary>
    private sealed class SearchRow
    {
        public string ObservationId { get; set; } = "";
        public string CelestialObject { get; set; } = "";
        public string? ObjectType { get; set; }
        public string LocationInSky { get; set; } = "";
        public string ObservationDetails { get; set; } = "";
        public string? AdditionalComments { get; set; }
        public string? ObservedTime { get; set; }
        public bool HasPhoto { get; set; }
        public string SessionId { get; set; } = "";
        public string SessionTitle { get; set; } = "";
        public string SessionDate { get; set; } = "";
        public string SessionTime { get; set; } = "";
        public string ObservingLocation { get; set; } = "";
        public string? ObserverName { get; set; }
        public string? Equipment { get; set; }
        public string? SkyConditions { get; set; }
        public long TotalCount { get; set; }

        public CelestialObservation ToObservation() => new()
        {
            Id = ObservationId,
            ObjectName = CelestialObject,
            ObjectType = ObjectType,
            SkyLocation = LocationInSky,
            Details = ObservationDetails,
            Comments = AdditionalComments,
            ObservedTime = ObservedTime,
            HasPhoto = HasPhoto,
            Session = new ObservingSession
            {
                CloudId = SessionId,
                Name = SessionTitle,
                Date = SessionDate,
                Time = SessionTime,
                Location = ObservingLocation,
                ObserverName = ObserverName,
                Equipment = Equipment,
                SkyConditions = SkyConditions
            }
        };
    }

    public void ClearCache()
    {
        if (File.Exists(_cacheFile)) File.Delete(_cacheFile);
        Preferences.Default.Remove(KeyCacheTime);
    }

    // ---------------------------------------------------------------- searching (offline, on the device)

    /// <summary>
    /// Search used on the Observations tab: matches object name, type, sky location,
    /// session name, location and observer (case-insensitive). Optional filter by object type.
    /// Works offline because it searches the downloaded/cached data.
    /// </summary>
    public IEnumerable<CelestialObservation> Search(string? query, string? objectType = null)
    {
        IEnumerable<CelestialObservation> result = Observations;

        if (!string.IsNullOrWhiteSpace(objectType))
        {
            result = result.Where(o => string.Equals(o.ObjectType, objectType, StringComparison.OrdinalIgnoreCase));
        }

        if (!string.IsNullOrWhiteSpace(query))
        {
            string q = query.Trim();
            result = result.Where(o =>
                Contains(o.ObjectName, q) || Contains(o.ObjectType, q) || Contains(o.SkyLocation, q) ||
                Contains(o.Session.Name, q) || Contains(o.Session.Location, q) || Contains(o.Session.ObserverName, q));
        }
        return result;
    }

    /// <summary>All observations of one catalogue object (e.g. every "Jupiter" observation).</summary>
    public IEnumerable<CelestialObservation> ForObject(CelestialObjectInfo info) =>
        Observations.Where(o => info.Matches(o.ObjectName));

    /// <summary>Distinct object types present in the data, for the filter picker.</summary>
    public IEnumerable<string> ObjectTypes() =>
        Observations.Select(o => o.ObjectType)
                    .Where(t => !string.IsNullOrWhiteSpace(t))
                    .Select(t => t!)
                    .Distinct(StringComparer.OrdinalIgnoreCase)
                    .Order();

    private static bool Contains(string? value, string q) =>
        value is not null && value.Contains(q, StringComparison.OrdinalIgnoreCase);

    // ---------------------------------------------------------------- internals

    private async Task<LoadResult> LoadFromCacheAsync(string reason)
    {
        if (!File.Exists(_cacheFile))
        {
            SetData([]);
            return new LoadResult(false, false, $"{reason} No saved data yet.");
        }
        try
        {
            string json = await File.ReadAllTextAsync(_cacheFile);
            SetData(Parse(json));
            string when = Preferences.Default.Get(KeyCacheTime, "earlier");
            return new LoadResult(true, true, $"{reason} Showing saved data from {when}.");
        }
        catch (JsonException)
        {
            SetData([]);
            return new LoadResult(false, false, $"{reason} Saved data could not be read.");
        }
    }

    /// <summary>Supabase returns a JSON array of sessions ("[]" when there is no data yet).</summary>
    private static List<ObservingSession> Parse(string json)
    {
        if (string.IsNullOrWhiteSpace(json) || !json.TrimStart().StartsWith('[')) return [];
        return JsonSerializer.Deserialize<List<ObservingSession>>(json, JsonOptions) ?? [];
    }

    private void SetData(List<ObservingSession> sessions)
    {
        var ordered = sessions
            .OrderByDescending(s => s.Date)
            .ThenByDescending(s => s.Time)
            .ToList();

        var observations = new List<CelestialObservation>();
        foreach (var session in ordered)
        {
            // Be defensive: Observations can be missing or contain nulls in hand-edited/cached data
            foreach (var obs in (session.Observations ?? []).Where(o => o is not null))
            {
                obs.Session = session;
                observations.Add(obs);
            }
        }

        Sessions = ordered;
        Observations = observations;
        HasLoaded = true;
        DataChanged?.Invoke(this, EventArgs.Empty);
    }
}
