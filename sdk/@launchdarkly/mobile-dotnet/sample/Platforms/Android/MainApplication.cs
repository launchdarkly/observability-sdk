using Android.App;
using Android.Runtime;

namespace MauiSample9;

[Application]
public class MainApplication : MauiApplication
{
	public MainApplication(IntPtr handle, JniHandleOwnership ownership)
		: base(handle, ownership)
	{
	}

	protected override MauiApp CreateMauiApp() { 
		var app = MauiProgram.CreateMauiApp();
		return app;
	}
}
