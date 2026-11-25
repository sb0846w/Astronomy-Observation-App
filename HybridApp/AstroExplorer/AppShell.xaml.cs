using AstroExplorer.Views;

namespace AstroExplorer;

public partial class AppShell : Shell
{
    public AppShell()
    {
        InitializeComponent();

        // Detail pages are pushed on top of the tabs with Shell.Current.GoToAsync(route, parameters)
        Routing.RegisterRoute(nameof(ObservationDetailPage), typeof(ObservationDetailPage));
        Routing.RegisterRoute(nameof(ObjectInfoPage), typeof(ObjectInfoPage));
    }
}
