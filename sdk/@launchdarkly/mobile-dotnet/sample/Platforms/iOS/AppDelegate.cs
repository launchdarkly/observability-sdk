using System;
using Foundation;
using LaunchDarkly.SessionReplay;

namespace MauiSample9;

[Register("AppDelegate")]
public class AppDelegate : MauiUIApplicationDelegate
{
	protected override MauiApp CreateMauiApp() { 
		Console.WriteLine("AppDelegate: CreateMauiApp");
		var app = MauiProgram.CreateMauiApp();
		return app;
	}
}
