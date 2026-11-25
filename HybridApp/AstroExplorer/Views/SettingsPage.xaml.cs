using AstroExplorer.Services;

namespace AstroExplorer.Views;

/// <summary>Theme choice, cloud URL and offline cache management (feature f).</summary>
public partial class SettingsPage : ContentPage
{
    private readonly SettingsService _settings;
    private readonly CloudService _cloud;
    private bool _loading;

    public SettingsPage(SettingsService settings, CloudService cloud)
    {
        InitializeComponent();
        _settings = settings;
        _cloud = cloud;
    }

    protected override void OnAppearing()
    {
        base.OnAppearing();
        _loading = true; // don't treat filling the picker as a user change
        ThemePicker.SelectedIndex = _settings.ThemeChoice;
        _loading = false;

        UrlEntry.Text = _settings.IsDatabaseConfigured ? _settings.DatabaseUrl : "";
        UpdateStats();
    }

    private void UpdateStats()
    {
        StatsLabel.Text = _cloud.HasLoaded
            ? $"{_cloud.Sessions.Count} sessions and {_cloud.Observations.Count} observations loaded."
            : "No data loaded yet.";
    }

    private void OnThemeChanged(object? sender, EventArgs e)
    {
        if (_loading || ThemePicker.SelectedIndex < 0) return;
        _settings.ThemeChoice = ThemePicker.SelectedIndex;
        _settings.ApplyTheme(); // takes effect immediately thanks to AppThemeBinding
    }

    private async void OnSaveUrlClicked(object? sender, EventArgs e)
    {
        string url = UrlEntry.Text?.Trim() ?? "";
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || uri.Scheme != Uri.UriSchemeHttps)
        {
            await DisplayAlert("Invalid URL", "Please enter a full https:// address.", "OK");
            return;
        }
        _settings.DatabaseUrl = url;
        await RunBusy(async () =>
        {
            var result = await _cloud.RefreshAsync();
            ConnectionResult.Text = "Saved. " + result.Message;
        });
        UpdateStats();
    }

    private async void OnTestClicked(object? sender, EventArgs e)
    {
        string url = UrlEntry.Text?.Trim() ?? "";
        if (string.IsNullOrEmpty(url))
        {
            ConnectionResult.Text = "Enter a URL first.";
            return;
        }
        await RunBusy(async () => ConnectionResult.Text = await _cloud.TestConnectionAsync(url));
    }

    private async void OnClearCacheClicked(object? sender, EventArgs e)
    {
        bool confirm = await DisplayAlert("Clear saved data?",
            "The offline copy will be deleted. Data in the cloud is not affected.", "Clear", "Cancel");
        if (!confirm) return;
        _cloud.ClearCache();
        await DisplayAlert("Done", "Saved data cleared.", "OK");
    }

    private async Task RunBusy(Func<Task> action)
    {
        Busy.IsVisible = Busy.IsRunning = true;
        try
        {
            await action();
        }
        finally
        {
            Busy.IsVisible = Busy.IsRunning = false;
        }
    }
}
