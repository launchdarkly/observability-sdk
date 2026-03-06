using Microsoft.Maui.Controls;
#if IOS
using UIKit;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif


namespace LaunchDarkly.SessionReplay;

public static class LDViewExtensions
{
    public static void LDMask(this View view)
    {
        #if IOS
        if (view?.Handler?.PlatformView is UIView uiView)
            LDMasking.Mask(uiView);
        #elif ANDROID
        if (view?.Handler?.PlatformView is Android.Views.View nativeView)
            LDMasking.Mask(nativeView);
        #endif
    }

    public static void LDUnmask(this View view)
    {
        #if IOS
        if (view?.Handler?.PlatformView is UIView uiView)
            LDMasking.Unmask(uiView);
        #elif ANDROID
        if (view?.Handler?.PlatformView is Android.Views.View nativeView)
            LDMasking.Unmask(nativeView);
        #endif
    }
}
