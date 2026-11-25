using AstroExplorer.Models;
using AstroExplorer.Services;

namespace AstroExplorer.Views;

/// <summary>
/// Feature e: information about celestial objects for amateur astronomers.
/// Each entry shows how many uploaded observations exist for that object.
/// Favourites (feature f) are saved on the device with Preferences.
/// </summary>
public partial class CataloguePage : ContentPage
{
    private readonly CatalogueService _catalogue;
    private readonly CloudService _cloud;
    private readonly SettingsService _settings;

    public CataloguePage(CatalogueService catalogue, CloudService cloud, SettingsService settings)
    {
        InitializeComponent();
        _catalogue = catalogue;
        _cloud = cloud;
        _settings = settings;
    }

    protected override async void OnAppearing()
    {
        base.OnAppearing();
        Refresh(); // show the catalogue straight away…
        if (!_cloud.HasLoaded)
        {
            await _cloud.EnsureLoadedAsync(); // …then fill in observation counts once data arrives
            Refresh();
        }
    }

    private void Refresh()
    {
        var favourites = _settings.GetFavourites();
        var items = _catalogue.Search(SearchBox.Text).ToList();

        foreach (var info in items)
        {
            info.IsFavourite = favourites.Contains(info.Name);
            info.ObservationCount = _cloud.ForObject(info).Count();
        }
        if (FavouritesSwitch.IsToggled)
        {
            items = items.Where(i => i.IsFavourite).ToList();
        }

        // Objects that have been observed most come first
        items = items.OrderByDescending(i => i.ObservationCount).ThenBy(i => i.Name).ToList();

        ObjectList.ItemsSource = null; // force the list to redraw the updated values
        ObjectList.ItemsSource = items;
    }

    private void OnSearchTextChanged(object? sender, TextChangedEventArgs e) => Refresh();

    private void OnFavouritesToggled(object? sender, ToggledEventArgs e) => Refresh();

    private async void OnObjectSelected(object? sender, SelectionChangedEventArgs e)
    {
        if (e.CurrentSelection.FirstOrDefault() is not CelestialObjectInfo info) return;
        ObjectList.SelectedItem = null;
        await Shell.Current.GoToAsync(nameof(ObjectInfoPage),
            new Dictionary<string, object> { ["Object"] = info });
    }
}
