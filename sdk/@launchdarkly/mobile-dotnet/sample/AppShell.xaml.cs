namespace MauiSample9;

public partial class AppShell : Shell
{
	public AppShell()
	{
		InitializeComponent();

		Routing.RegisterRoute(nameof(CreditCardPage), typeof(CreditCardPage));
		Routing.RegisterRoute(nameof(NumberPadPage), typeof(NumberPadPage));
	}
}
