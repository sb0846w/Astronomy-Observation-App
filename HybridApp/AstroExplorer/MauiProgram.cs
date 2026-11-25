using AstroExplorer.Services;
using AstroExplorer.Views;

namespace AstroExplorer;

public static class MauiProgram
{
    public static MauiApp CreateMauiApp()
    {
        var builder = MauiApp.CreateBuilder();
        builder.UseMauiApp<App>();

        // Services are singletons: one shared copy of the downloaded data and settings.
        builder.Services.AddSingleton<SettingsService>();
        builder.Services.AddSingleton<CloudService>();
        builder.Services.AddSingleton<CatalogueService>();

        // Pages are created by dependency injection so they receive the services above.
        // Transient = a fresh page each time a new Shell/window is created (avoids "page already has a parent").
        builder.Services.AddTransient<ObservationsPage>();
        builder.Services.AddTransient<CataloguePage>();
        builder.Services.AddTransient<SettingsPage>();
        builder.Services.AddTransient<ObservationDetailPage>();
        builder.Services.AddTransient<ObjectInfoPage>();

        return builder.Build();
    }
}
