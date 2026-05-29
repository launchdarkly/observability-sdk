using System;
using System.Collections.Generic;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using LaunchDarkly.SessionReplay;
using System.Reflection;
using System.Threading.Tasks;
using LaunchDarkly.Sdk.Client;
using LaunchDarkly.Observability;
using CommunityToolkit.Maui;
using Microsoft.Maui.Controls.Hosting;
using Microsoft.Maui.Hosting;
using Plugin.Maui.BottomSheet.Hosting;
namespace MauiSample9;

public static class MauiProgram
{
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
            .UseMauiApp<App>()
            .UseMauiCommunityToolkit()
            .UseBottomSheet();

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
        var ldConfig = Configuration.Builder(mobileKey, LaunchDarkly.Sdk.Client.ConfigurationBuilder.AutoEnvAttributes.Enabled)
        // .Plugins(new PluginConfigurationBuilder()
        // 	.Add(observabilityPlugin)
        // 	.Add(sessionReplayPlugin)
        .Build();

        var context = LaunchDarkly.Sdk.Context.New("maui-user-key");
	
        var observabilityOptions = new ObservabilityOptions(
	        isEnabled: true,
#if IOS
	        serviceName: "maui-ios-sample",
#elif ANDROID
            serviceName: "maui-android-sample",
#else
            serviceName: "maui-sample-app",
#endif
	        otlpEndpoint: otlpEndpoint,
	        backendUrl: backendUrl,
	        attributes: new Dictionary<string, object?> { { "test-options-attribute", "maui-sample-value" } },
	        instrumentation: new (
		        networkRequests: true,
		        launchTimes: true
	        )
        );

        var replayOptions = new SessionReplayOptions(
	        isEnabled: true,
	        privacy: new (
		        maskTextInputs: true,
		        maskWebViews: false,
		        maskLabels: false
	        )
        );
        //async variant:
		_ = Task.Run(async () =>
        {
            try
            {
	            // standalone variant (no LaunchDarkly client):
	            // LDObserve.Init(mobileKey, observabilityOptions, replayOptions);

	            var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(0));
	            LDObserve.Init(client, observabilityOptions, replayOptions);
             //    var feature1 = client.BoolVariation("feature1", false);
             //    Console.WriteLine($"feature1 sync value ={feature1}");
             //
             //    client.FlagTracker.FlagValueChanged += (sender, eventArgs) =>
             //    {
             //        if (eventArgs.Key == "feature1")
             //        {
             //            Console.WriteLine($"feature1 changed from {eventArgs.OldValue} to {eventArgs.NewValue}");
             //        }
             //    };

                LDObserve.RecordMetric("maui-app-start", 1.0);
                LDObserve.RecordLog("maui-app-start", Severity.Info, new Dictionary<string, object?> { { "event", "app_start" } });
                var span = LDObserve.StartActiveSpan("maui-app-start");
                span?.End();
            }
            catch (Exception ex)
            {
                Console.WriteLine($"LD init/startup failed: {ex}");
            }
        });

        //sync variant: var client = LdClient.Init(ldConfig, context, TimeSpan.FromSeconds(0));
        
        return app;
    }
}
