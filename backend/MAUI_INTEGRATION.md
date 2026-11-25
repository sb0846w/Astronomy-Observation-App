# .NET MAUI integration (AstroExplorer)

The hybrid app only **reads**, so it uses just the publishable key (role `anon`) and never signs in. RLS lets `anon` read every session and observation but write nothing.

| File | Role |
|---|---|
| `Services/SettingsService.cs` | project URL (`DefaultDatabaseUrl`, editable on the Settings tab) and `PublishableKey` |
| `Services/CloudService.cs` | paged download, offline cache, local search, server search (`SearchCloudAsync`) |
| `Models/ObservingSession.cs`, `Models/CelestialObservation.cs` | JSON ↔ C# mapping |

## 1. Connect to the same project

```csharp
var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
http.DefaultRequestHeaders.Add("apikey", SettingsService.PublishableKey);   // publishable key only

var json = new JsonSerializerOptions
{
    PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,   // DurationHours ⇄ duration_hours
    PropertyNameCaseInsensitive = true
};
```

Where a C# property name differs from the column, the model says so:

```csharp
[JsonPropertyName("session_title")]          public string Name { get; set; } = "";
[JsonPropertyName("celestial_observations")] public List<CelestialObservation>? Observations { get; set; }
[JsonPropertyName("celestial_object")]       public string ObjectName { get; set; } = "";
```

## 2. Retrieve sessions with their observations (with pagination)

One request per page. Each session comes back with its observations embedded (PostgREST follows the foreign key):

```csharp
// CloudService.DownloadAllSessionsAsync() – shortened
for (int page = 0; page < MaxPages; page++)
{
    string url = $"{baseUrl}/rest/v1/observing_sessions?select=*,celestial_observations(*)" +
                 "&celestial_observations.order=sort_order" +
                 "&order=session_date.desc,session_time.desc,id" +      // stable order = safe paging
                 $"&limit={PageSize}&offset={page * PageSize}";
    string pageJson = await http.GetStringAsync(url);
    // … collect the page; stop when it has fewer than PageSize items
}
```

The combined result is saved to `sessions_cache_v2.json`, so the app still works offline (feature f).

To get a total for a pager, add the header `Prefer: count=exact`. The reply's `Content-Range` header then looks like `0-99/250`. `TestConnectionAsync` uses this for the Settings tab.

## 3. Search for a celestial object

**a) Offline, on the device** (used by the Observations tab): `CloudService.Search(query, type)` filters the downloaded list by name, type, sky location, session, location or observer.

**b) On the server, with pagination**: `CloudService.SearchCloudAsync(query, limit, offset)` calls the `search_observations` database function:

```csharp
SearchPage page = await cloud.SearchCloudAsync("Jupiter", limit: 20, offset: 0);

if (page.Error is not null)      ShowError(page.Error);              // offline, invalid input, server error
else if (page.IsEmpty)           ShowEmpty("No observations of Jupiter yet.");
else
{
    Results.ItemsSource = page.Items;                                // CelestialObservation with .Session filled in
    LoadMoreButton.IsVisible = page.Items.Count + offset < page.TotalCount;
}
```

The underlying request:

```http
POST /rest/v1/rpc/search_observations
apikey: sb_publishable_…
Content-Type: application/json

{ "p_query": "Jupiter", "p_limit": 20, "p_offset": 0 }
```

Each result row contains the observation **and** its session (`session_title`, `session_date`, `observing_location`, `observer_name`, …), plus `total_count`. The method turns each row into a `CelestialObservation` whose `Session` property is filled in, so the existing XAML templates (`SessionSummary`, `TypeAndLocation`, …) work unchanged.

Searching is case-insensitive and partial, with exact matches first:

| Search text | Finds |
|---|---|
| `Jupiter`, `jupiter`, `JUP` | every Jupiter observation |
| `Saturn` | Saturn |
| `moon` | Moon |
| `orion` / `Orion Nebula` | Orion Nebula (M42) |
| `andromeda` | Andromeda Galaxy (M31) |

> `SearchCloudAsync` is implemented and the database function is tested, but no page calls it yet. The Observations tab keeps its offline search. A good "own improvement": use `SearchCloudAsync` when online, with a **Load more** button that uses `offset`.

## 4. Loading, empty and error states

| State | How the app detects it | What to show |
|---|---|---|
| Loading | while the `await` is running | `ActivityIndicator` / `RefreshView.IsRefreshing` |
| Empty | `Sessions.Count == 0` or `SearchPage.IsEmpty` | "No observations yet. Upload some from AstroLog." |
| Offline | `Connectivity.Current.NetworkAccess != Internet` | "You are offline. Showing saved data from …" (`LoadResult.FromCache`) |
| Server or network error | `HttpRequestException`, `TaskCanceledException` (timeout) | "Could not reach the cloud service." plus the cached data |
| Bad search input | HTTP 400 with `{"message":"search_query_required","details":"Type at least one character…"}` | `SearchPage.Error` (the `details` text) |

`RefreshAsync()` returns a `LoadResult(Success, FromCache, Message)` that the pages already show in their status line.
