using AstroExplorer.Models;
using AstroExplorer.Services;

namespace AstroExplorer.Views;

/// <summary>
/// Shows one observation with its session details, links to the object's
/// information page, and (feature f) opens the observing site in the maps app.
/// Receives the observation through Shell navigation parameters.
/// </summary>
public partial class ObservationDetailPage : ContentPage, IQueryAttributable
{
    private readonly CatalogueService _catalogue;
    private CelestialObservation? _observation;
    private CelestialObjectInfo? _info;

    public ObservationDetailPage(CatalogueService catalogue)
    {
        InitializeComponent();
        _catalogue = catalogue;
    }

    public void ApplyQueryAttributes(IDictionary<string, object> query)
    {
        if (!query.TryGetValue("Observation", out var value) || value is not CelestialObservation obs) return;

        _observation = obs;
        BindingContext = obs;
        Title = obs.ObjectName;

        _info = _catalogue.FindFor(obs.ObjectName);
        if (_info is not null)
        {
            InfoCard.IsVisible = true;
            InfoSummary.Text = $"{_info.Name} is a {_info.Type.ToLowerInvariant()} in {_info.Constellation}. Distance: {_info.Distance}.";
            InfoButton.Text = $"About {_info.Name}";
        }
    }

    private async void OnLearnMoreClicked(object? sender, EventArgs e)
    {
        if (_info is null) return;
        await Shell.Current.GoToAsync(nameof(ObjectInfoPage),
            new Dictionary<string, object> { ["Object"] = _info });
    }

    private async void OnMapClicked(object? sender, EventArgs e)
    {
        var session = _observation?.Session;
        if (session?.Latitude is null || session.Longitude is null) return;
        try
        {
            await Map.Default.OpenAsync(session.Latitude.Value, session.Longitude.Value,
                new MapLaunchOptions { Name = session.Location });
        }
        catch (Exception)
        {
            await DisplayAlert("Maps", "No maps app is available on this device.", "OK");
        }
    }

    private async void OnShareClicked(object? sender, EventArgs e)
    {
        if (_observation is null) return;
        var o = _observation;
        string text = $"I found this observation of {o.ObjectName} on AstroExplorer:\n" +
                      $"{o.Details}\n" +
                      $"Seen by {o.Session.ObserverDisplay} from {o.Session.Location} on {o.Session.DisplayDate}.";
        await Share.Default.RequestAsync(new ShareTextRequest { Title = o.ObjectName, Text = text });
    }
}
