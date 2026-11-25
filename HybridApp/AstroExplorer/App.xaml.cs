using AstroExplorer.Services;

namespace AstroExplorer;

public partial class App : Application
{
    public App(SettingsService settings)
    {
        InitializeComponent();
        settings.ApplyTheme(); // restore the user's light/dark choice (feature f)
    }

    // .NET 9+ way of setting the first page (replaces the old MainPage property).
    // A new Shell per window, because Android can recreate the window.
    protected override Window CreateWindow(IActivationState? activationState) =>
        new Window(new AppShell()) { Title = "AstroExplorer" };
}
