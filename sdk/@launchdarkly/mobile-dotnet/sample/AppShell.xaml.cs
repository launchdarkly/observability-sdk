namespace MauiSample9;

public partial class AppShell : Shell
{
	public AppShell()
	{
		InitializeComponent();

		MainContent.Title = BuildTitle();

		Routing.RegisterRoute(nameof(CreditCardPage), typeof(CreditCardPage));
		Routing.RegisterRoute(nameof(NumberPadPage), typeof(NumberPadPage));
		Routing.RegisterRoute(nameof(DialogsPage), typeof(DialogsPage));
	}

	// MauiSample9 and MauiSample10 share these sources, so the title is what tells the two
	// builds apart on a device.
	private static string BuildTitle()
	{
#if DEBUG
		const string configuration = "Debug";
#else
		const string configuration = "Release";
#endif
		var platform = DeviceInfo.Platform == DevicePlatform.WinUI ? "Windows" : DeviceInfo.Platform.ToString();
		return $"{platform} · .NET {Environment.Version.Major} · {configuration}";
	}
}
