using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using LaunchDarkly.SessionReplay;
using System.Reflection;
using LaunchDarkly.Sdk.Client;
using LaunchDarkly.Sdk.Client.Interfaces;
namespace MauiSample9;

public static class MauiProgram
{
	public static LDNative? LdNative { get; private set; }

	private static IConfiguration BuildConfiguration()
	{
		var assembly = Assembly.GetExecutingAssembly();
		var configBuilder = new Microsoft.Extensions.Configuration.ConfigurationBuilder();

		configBuilder.AddJsonStream(assembly.GetManifestResourceStream("MauiSample9.appsettings.json")!);

		var localStream = assembly.GetManifestResourceStream("MauiSample9.appsettings.Local.json");
		if (localStream is not null)
			configBuilder.AddJsonStream(localStream);

		return configBuilder.Build();
	}

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

		var config = BuildConfiguration();

		var app = builder.Build();

		var mobileKey = config["LaunchDarkly:MobileKey"]
			?? throw new InvalidOperationException(
				"LaunchDarkly:MobileKey not found. " +
				"Copy appsettings.json to appsettings.Local.json and set your key.");

		var otlpEndpoint = config["LaunchDarkly:OtlpEndpoint"];
		var backendUrl = config["LaunchDarkly:BackendUrl"];

		var ldConfig = Configuration.Builder(mobileKey, LaunchDarkly.Sdk.Client.ConfigurationBuilder.AutoEnvAttributes.Enabled).Build();
		var context = LaunchDarkly.Sdk.Context.New("maui-user-key");
		var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(10));
		var feature1 = client.BoolVariation("feature1", false);
		Console.WriteLine($"feature1 sync value ={feature1}");

		client.FlagTracker.FlagValueChanged += (sender, eventArgs) => {
			if (eventArgs.Key == "feature1") {
				Console.WriteLine($"feature1 changed from {eventArgs.OldValue} to {eventArgs.NewValue}");
			}
		};

		LdNative = LDNative.Start(
			mobileKey: mobileKey,
			observability: new ObservabilityOptions(
				serviceName: "maui-sample-app",
				otlpEndpoint: otlpEndpoint,
				backendUrl: backendUrl
			),
			replay: new SessionReplayOptions(
				isEnabled: true,
				privacy: new SessionReplayOptions.PrivacyOptions(
					maskTextInputs: true,
					maskWebViews: false,
					maskLabels: false
				)
			)
		);
		LdNative.Replay.IsEnabled = true;
		Console.WriteLine($"ldNative.version={LdNative.NativeVersion}");
		return app;
	}
}
