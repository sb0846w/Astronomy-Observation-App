using AstroExplorer.Models;
using AstroExplorer.Services;

namespace AstroExplorer.Views;

/// <summary>
/// Information about one celestial object plus every uploaded observation of it
/// (feature e: "search for a particular celestial object and see a list of observations").
/// </summary>
public partial class ObjectInfoPage : ContentPage, IQueryAttributable
{
    private readonly CloudService _cloud;
    private readonly SettingsService _settings;
    private CelestialObjectInfo? _info;

    public ObjectInfoPage(CloudService cloud, SettingsService settings)
    {
        InitializeComponent();
        _cloud = cloud;
        _settings = settings;
    }

    public void ApplyQueryAttributes(IDictionary<string, object> query)
    {
        if (!query.TryGetValue("Object", out var value) || value is not CelestialObjectInfo info) return;
        _info = info;
        BindingContext = info;
        UpdateFavouriteButton();

        var observations = _cloud.ForObject(info).ToList();
        BindableLayout.SetItemsSource(ObservationsStack, observations);
        ObservationsHeader.Text = observations.Count == 1
            ? "1 observation from the community"
            : $"{observations.Count} observations from the community";
        NoObservationsLabel.IsVisible = observations.Count == 0;
    }

    private void UpdateFavouriteButton()
    {
        if (_info is null) return;
        FavouriteItem.Text = _settings.IsFavourite(_info.Name) ? "♥ Favourite" : "♡ Add favourite";
    }

    private void OnFavouriteClicked(object? sender, EventArgs e)
    {
        if (_info is null) return;
        _info.IsFavourite = _settings.ToggleFavourite(_info.Name);
        UpdateFavouriteButton();
    }

    private async void OnObservationTapped(object? sender, TappedEventArgs e)
    {
        if ((sender as BindableObject)?.BindingContext is not CelestialObservation obs) return;
        await Shell.Current.GoToAsync(nameof(ObservationDetailPage),
            new Dictionary<string, object> { ["Observation"] = obs });
    }
}
