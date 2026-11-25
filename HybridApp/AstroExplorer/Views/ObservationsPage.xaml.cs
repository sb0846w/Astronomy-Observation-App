using AstroExplorer.Models;
using AstroExplorer.Services;

namespace AstroExplorer.Views;

/// <summary>
/// Feature e: lists every observation uploaded from the Android app and lets the user
/// search for a particular celestial object (e.g. "Jupiter") and filter by type.
/// </summary>
public partial class ObservationsPage : ContentPage
{
    private const string AllTypes = "All types";

    private readonly CloudService _cloud;
    private string _lastStatus = "";

    public ObservationsPage(CloudService cloud)
    {
        InitializeComponent();
        _cloud = cloud;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _cloud.DataChanged += OnDataChanged;
        RebuildTypeFilter();
        if (!_cloud.HasLoaded)
        {
            Refresher.IsRefreshing = true; // raises Refreshing → OnRefreshing does the first download
        }
        else
        {
            ApplyFilter();
        }
    }

    protected override void OnDisappearing()
    {
        base.OnDisappearing();
        _cloud.DataChanged -= OnDataChanged; // avoid keeping this page alive from the service
    }

    private void OnDataChanged(object? sender, EventArgs e) =>
        MainThread.BeginInvokeOnMainThread(RebuildTypeFilter);

    private void OnRefreshClicked(object? sender, EventArgs e)
    {
        if (!Refresher.IsRefreshing) Refresher.IsRefreshing = true; // runs OnRefreshing
    }

    private async void OnRefreshing(object? sender, EventArgs e)
    {
        LoadResult result = await _cloud.RefreshAsync();
        _lastStatus = result.Message;
        Refresher.IsRefreshing = false;
        ApplyFilter();
    }

    /// <summary>Fills the type picker with the types that actually appear in the data.</summary>
    private void RebuildTypeFilter()
    {
        string? previous = TypePicker.SelectedItem as string;
        var types = new List<string> { AllTypes };
        types.AddRange(_cloud.ObjectTypes());
        TypePicker.ItemsSource = types;
        TypePicker.SelectedItem = previous is not null && types.Contains(previous) ? previous : AllTypes;
    }

    private void ApplyFilter()
    {
        string? type = TypePicker.SelectedItem as string;
        if (type == AllTypes) type = null;

        var results = _cloud.Search(SearchBox.Text, type).ToList();
        ObservationList.ItemsSource = results;

        bool filtered = !string.IsNullOrWhiteSpace(SearchBox.Text) || type is not null;
        StatusLabel.Text = filtered
            ? $"{results.Count} matching observation(s)   {_lastStatus}"
            : _lastStatus;
    }

    private void OnSearchTextChanged(object? sender, TextChangedEventArgs e) => ApplyFilter();

    private void OnSearchPressed(object? sender, EventArgs e)
    {
        ApplyFilter();
        SearchBox.Unfocus(); // hide the keyboard
    }

    private void OnTypeChanged(object? sender, EventArgs e) => ApplyFilter();

    private async void OnObservationSelected(object? sender, SelectionChangedEventArgs e)
    {
        if (e.CurrentSelection.FirstOrDefault() is not CelestialObservation obs) return;
        ObservationList.SelectedItem = null; // allow tapping the same item again later

        await Shell.Current.GoToAsync(nameof(ObservationDetailPage),
            new Dictionary<string, object> { ["Observation"] = obs });
    }
}
