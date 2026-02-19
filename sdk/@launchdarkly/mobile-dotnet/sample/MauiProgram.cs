using Microsoft.Extensions.Logging;
using LaunchDarkly.SessionReplay;
using System.Reflection;

namespace MauiSample9;

public static class MauiProgram
{
	private static void LogMauiAssemblyInfo()
	{
		try
		{
			var mauiAsm = typeof(Microsoft.Maui.Handlers.FlyoutViewHandler).Assembly;
			var controlsAsm = typeof(Microsoft.Maui.Controls.Application).Assembly;
			var mapperField = typeof(Microsoft.Maui.Handlers.FlyoutViewHandler).GetField(
				"Mapper",
				BindingFlags.Public | BindingFlags.Static
			);

			var msg =
				$"MAUI assembly probe:\n" +
				$"- Microsoft.Maui: {mauiAsm.FullName}\n" +
				$"- Microsoft.Maui.Controls: {controlsAsm.FullName}\n" +
				$"- FlyoutViewHandler.Mapper exists: {mapperField is not null}";

			System.Diagnostics.Debug.WriteLine(msg);
			Console.WriteLine(msg);


		}
		catch (Exception ex)
		{
			System.Diagnostics.Debug.WriteLine(ex);
			Console.WriteLine(ex);

		}
	}

	public static MauiApp CreateMauiApp()
	{
		var builder = MauiApp.CreateBuilder();
		LogMauiAssemblyInfo();

		builder
			.UseMauiApp<App>();

#if DEBUG
		builder.Logging.AddDebug();
#endif

		var app = builder.Build();

		var mobileKey = "yourkey";

		var ldNative = LDNative.Start(
			mobileKey: mobileKey,
			observability: new ObservabilityOptions(
				serviceName: "maui-sample-app",
				otlpEndpoint: "https://otel.observability.ld-stg.launchdarkly.com:4318",
				backendUrl: "https://pub.observability.ld-stg.launchdarkly.com/"
			),
			replay: new SessionReplayOptions(
				isEnabled: true,
				privacy: new SessionReplayOptions.PrivacyOptions(
					maskTextInputs: true,
					maskWebViews: false
				)
			)
		);
		ldNative.Replay.IsEnabled = true;
		Console.WriteLine($"ldNative.version={ldNative.NativeVersion}");
		return app;
	}
}
