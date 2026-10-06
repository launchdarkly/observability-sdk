using System;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;
using Microsoft.Maui;
using Microsoft.Maui.ApplicationModel;
using Microsoft.Maui.Controls;
using Microsoft.Maui.Handlers;
#if IOS
using UIKit;
using LDObserveMaciOS;
#elif ANDROID
using LDObserveAndroid;
#endif

namespace LaunchDarkly.SessionReplay;

/// <summary>
/// Applies <see cref="SessionReplayOptions.PrivacyOptions"/> MAUI-type rules by marking each
/// matching view's platform view through the native per-view masking API.
/// </summary>
internal static class PrivacyTypeRules
{
    private const string MappingKey = "LaunchDarkly.SessionReplay.PrivacyTypeRules";

    private enum Rule { None, Mask, Unmask, Ignore }

    private static readonly object Gate = new();
    private const int MaxApplicationAttempts = 100;
    private static readonly TimeSpan ApplicationRetryDelay = TimeSpan.FromMilliseconds(100);

    private static readonly ConditionalWeakTable<VisualElement, object> Watched = new();
    private static readonly ConditionalWeakTable<Window, object> WatchedWindows = new();
    private static HashSet<Type> _mask = new();
    private static HashSet<Type> _unmask = new();
    private static HashSet<Type> _ignore = new();
    private static bool _installed;

    internal static void Install(SessionReplayOptions.PrivacyOptions? privacy)
    {
        if (privacy is null)
            return;

        var mask = new HashSet<Type>(privacy.MaskViewTypes ?? Enumerable.Empty<Type>());
        var unmask = new HashSet<Type>(privacy.UnmaskViewTypes ?? Enumerable.Empty<Type>());
        var ignore = new HashSet<Type>(privacy.IgnoreViewTypes ?? Enumerable.Empty<Type>());
        if (mask.Count == 0 && unmask.Count == 0 && ignore.Count == 0)
            return;

        lock (Gate)
        {
            _mask = mask;
            _unmask = unmask;
            _ignore = ignore;
            if (_installed)
                return;
            _installed = true;
        }

        // Handler mappers and the visual tree are UI-thread only. Running inline when already on
        // the main thread keeps the mapper registered before the first page's handlers are built.
        if (MainThread.IsMainThread)
            Attach();
        else
            MainThread.BeginInvokeOnMainThread(Attach);
    }

    private static void Attach()
    {
        // Covers handlers created after this point, provided no handler has cached its mapper keys
        // yet (the usual case when LDObserve is initialized from CreateMauiApp).
        ViewHandler.ViewMapper.AppendToMapping(MappingKey, (handler, view) => Apply(view, handler.PlatformView));

        // The mapper misses new instances of types whose handlers already cached their mapper keys,
        // so the visual tree is watched as well.
        AttachToApplication(0);
    }

    private static void AttachToApplication(int attempt)
    {
        // Application.Current is still null when LDObserve is initialized from CreateMauiApp.
        if (Application.Current is not { } app)
        {
            if (attempt < MaxApplicationAttempts)
            {
                Task.Delay(ApplicationRetryDelay).ContinueWith(
                    _ => MainThread.BeginInvokeOnMainThread(() => AttachToApplication(attempt + 1)),
                    TaskScheduler.Default);
            }
            return;
        }

        app.DescendantAdded += (_, e) =>
        {
            if (e.Element is Window window)
                WatchWindow(window);
            else
                Watch(e.Element);
        };
        foreach (var window in app.Windows)
            WatchWindow(window);
    }

    // DescendantAdded is not guaranteed to bubble from a window's content up to the application,
    // so each window is watched directly too.
    private static void WatchWindow(Window window)
    {
        if (WatchedWindows.TryGetValue(window, out _))
            return;
        WatchedWindows.Add(window, new object());

        window.DescendantAdded += (_, e) => Watch(e.Element);
        if (window.Page is IVisualTreeElement root)
            Watch(root);
    }

    private static void Watch(object element)
    {
        if (element is VisualElement ve && RuleFor(ve) != Rule.None && !Watched.TryGetValue(ve, out _))
        {
            Watched.Add(ve, new object());
            ve.HandlerChanged += (_, _) => Apply(ve, ve.Handler?.PlatformView);
            Apply(ve, ve.Handler?.PlatformView);
        }

        if (element is IVisualTreeElement tree)
        {
            foreach (var child in tree.GetVisualChildren())
                Watch(child);
        }
    }

    private static Rule RuleFor(object view)
    {
        var type = view.GetType();
        lock (Gate)
        {
            if (_ignore.Contains(type)) return Rule.Ignore;
            if (_mask.Contains(type)) return Rule.Mask;
            if (_unmask.Contains(type)) return Rule.Unmask;
        }
        return Rule.None;
    }

    private static void Apply(IView view, object? platformView)
    {
        var rule = RuleFor(view);
        if (rule == Rule.None || platformView is null)
            return;

#if IOS
        if (platformView is not UIView uiView)
            return;
        switch (rule)
        {
            case Rule.Ignore: LDMasking.Ignore(uiView); break;
            case Rule.Mask: LDMasking.Mask(uiView); break;
            case Rule.Unmask: LDMasking.Unmask(uiView); break;
        }
#elif ANDROID
        if (platformView is not Android.Views.View nativeView)
            return;
        switch (rule)
        {
            // The Android SDK has no per-view ignore, so ignored views are covered instead.
            case Rule.Ignore:
            case Rule.Mask: LDMasking.Mask(nativeView); break;
            case Rule.Unmask: LDMasking.Unmask(nativeView); break;
        }
#endif
    }
}
