import SessionReplayReactNative from './NativeSessionReplayReactNative';

/**
 * Optional classifiers and attributes attached to a `screen_view`.
 */
export type LDScreenViewOptions = {
  /** The class or type of the screen, reported as `event.screen_class`. */
  screenClass?: string;
  /** A stable identifier for the screen, reported as `event.screen_id`. */
  screenId?: string;
  /** A grouping category for the screen, reported as `event.category`. */
  category?: string;
  /** Additional attributes attached to the `screen_view` span. */
  properties?: Record<string, unknown>;
};

// Native observability is started asynchronously (configure, then initializeSessionReplay, both
// run off the plugin's synchronous register), so a navigation tracker reporting the first route
// can beat it. Hold that screen view until native is up rather than dropping it: the opening
// `Navigate` should name the screen the session starts on. Only the most recent one is kept —
// a native SDK that has not started yet has no navigation history to rebuild.
let nativeStarted = false;
let pendingScreenView: { name: string; options?: LDScreenViewOptions } | null =
  null;
let missingNativeMethodWarned = false;

function nativeOptionsFrom(options?: LDScreenViewOptions): Object {
  const nativeOptions: Record<string, unknown> = {};
  if (options?.screenClass) {
    nativeOptions.screenClass = options.screenClass;
  }
  if (options?.screenId) {
    nativeOptions.screenId = options.screenId;
  }
  if (options?.category) {
    nativeOptions.category = options.category;
  }
  if (options?.properties) {
    nativeOptions.properties = options.properties;
  }
  return nativeOptions;
}

function forwardScreenView(name: string, options?: LDScreenViewOptions): void {
  // Feature-detected because a JS-only (OTA) update can run this bundle against an older native
  // binary with no `trackScreenView`. Screen views are then not reported, which beats throwing
  // out of a navigation callback.
  if (typeof SessionReplayReactNative.trackScreenView !== 'function') {
    if (!missingNativeMethodWarned) {
      missingNativeMethodWarned = true;
      console.warn(
        '[SessionReplay] The installed native session replay does not support screen views; update the native dependency to report them.'
      );
    }
    return;
  }
  SessionReplayReactNative.trackScreenView(
    name,
    nativeOptionsFrom(options)
  ).catch((error) => {
    console.error('[SessionReplay] Failed to report screen view:', error);
  });
}

/**
 * Records a screen view, emitting a `screen_view` span and a Session Replay `Navigate` timeline
 * event.
 *
 * React Native renders every route into the single host `UIViewController` / `Activity`, so the
 * native SDK's automatic screen detection never sees a route change — it is turned off for React
 * Native, and screens are reported from JS instead. Call this on each navigation, or attach
 * {@link createNavigationObserver} to a React Navigation container to do it for you.
 *
 * `event.previous_screen` is resolved natively from a shared navigation stack, so one call per
 * appearance is enough; back-navigation and re-appearance are handled there.
 *
 * Reporting is fire-and-forget: failures are logged rather than thrown, so this is safe to call
 * from a navigation callback or a render effect. A screen view reported before session replay
 * finishes starting is held and sent once it does.
 *
 * @param name The screen name, reported as `event.name`. Blank names are ignored.
 * @param options Optional classifiers and extra attributes.
 */
export function trackScreenView(
  name: string,
  options?: LDScreenViewOptions
): void {
  const screenName = typeof name === 'string' ? name.trim() : '';
  if (!screenName) {
    return;
  }
  if (!nativeStarted) {
    pendingScreenView = { name: screenName, options };
    return;
  }
  forwardScreenView(screenName, options);
}

/**
 * Flushes a screen view reported before native session replay finished starting.
 *
 * @internal
 */
export function notifyNativeStarted(): void {
  nativeStarted = true;
  const buffered = pendingScreenView;
  pendingScreenView = null;
  if (buffered) {
    forwardScreenView(buffered.name, buffered.options);
  }
}
