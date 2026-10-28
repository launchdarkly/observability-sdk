import { useEffect, useRef } from 'react';
import { trackScreenView, type LDScreenViewOptions } from './screenView';

/**
 * The shape of a navigation route this package reads. Structurally compatible with React
 * Navigation's `Route`, so its routes can be passed without importing the library.
 */
export type LDRoute = {
  /** Unique per route instance; used to tell a re-navigation from a route that stayed on top. */
  key?: string;
  /** The registered route name. */
  name?: string;
};

/**
 * Derives a screen name from a route. Return `null` or an empty string to skip the route (for
 * example an unnamed dialog or bottom sheet that should not read as a navigation).
 */
export type LDScreenNameExtractor = (route: LDRoute) => string | null | undefined;

/**
 * The subset of React Navigation's container ref this package uses. Pass the value returned by
 * `createNavigationContainerRef()` or a `useRef` attached to `NavigationContainer`.
 */
export type LDNavigationRef = {
  getCurrentRoute?: () => LDRoute | undefined;
};

export type LDNavigationObserverOptions = {
  /** Customizes how a screen name is derived from a route. Defaults to the route's name. */
  screenNameExtractor?: LDScreenNameExtractor;
  /** An optional classifier attached to every recorded screen view, e.g. `"navigation"`. */
  category?: string;
};

/**
 * Callbacks to spread onto a React Navigation container.
 */
export type LDNavigationObserver = {
  onReady: () => void;
  onStateChange: () => void;
};

/**
 * The path part of a route name: everything before the query string and fragment, with a
 * trailing slash removed.
 *
 * A query string is the one part of a route name that holds values rather than structure, so
 * `'/reset?token=abc123'` would put a secret into a name meant to identify a screen, and give
 * that screen a fresh identity per value. Only names that look like paths are treated as URLs —
 * a route name is free to be a sentence, and `'Delete this?'` is a legitimate name for a
 * confirmation screen, so anything not starting with `/` is returned untouched.
 *
 * Path parameters are not collapsed: `'/orders/42'` is reported as itself, since only the app
 * knows which segments are ids. Pass a `screenNameExtractor` to collapse them.
 */
export function screenNameFromRouteName(name: string): string {
  if (!name.startsWith('/')) {
    return name;
  }
  const queryStart = name.search(/[?#]/);
  const path = queryStart === -1 ? name : name.slice(0, queryStart);
  // `/orders/` and `/orders` are the same screen. The root stays `/`.
  return path.length > 1 && path.endsWith('/') ? path.slice(0, -1) : path;
}

/** Default extractor: the route's name, minus any query string or fragment. */
export function defaultScreenNameExtractor(route: LDRoute): string | undefined {
  return route.name === undefined
    ? undefined
    : screenNameFromRouteName(route.name);
}

/**
 * Reports React Navigation route changes as screen views.
 *
 * Spread the result onto your navigation container; `onReady` reports the first screen and
 * `onStateChange` every navigation after it:
 *
 * ```tsx
 * const navigationRef = createNavigationContainerRef();
 *
 * <NavigationContainer
 *   ref={navigationRef}
 *   {...createNavigationObserver(navigationRef)}
 * >
 * ```
 *
 * Only the route stack is observed. Navigation that does not change it — switching tabs rendered
 * from component state, paging a `ScrollView` — is invisible here and needs an explicit
 * {@link trackScreenView}.
 */
export function createNavigationObserver(
  navigationRef: LDNavigationRef,
  options: LDNavigationObserverOptions = {}
): LDNavigationObserver {
  const { screenNameExtractor = defaultScreenNameExtractor, category } = options;
  let lastKey: string | undefined;
  let lastName: string | undefined;

  const record = () => {
    const route = navigationRef.getCurrentRoute?.();
    if (!route) {
      return;
    }
    const name = screenNameExtractor(route);
    if (!name) {
      return;
    }
    // The same route instance being current again is not a navigation: dismissing a screen the
    // extractor skipped (typically an unnamed modal) leaves the one underneath it current. Keyed
    // on the route instance rather than the name, so pushing the same screen twice still reads
    // as two screen views.
    if (route.key !== undefined ? route.key === lastKey : name === lastName) {
      return;
    }
    lastKey = route.key;
    lastName = name;
    trackScreenView(name, { screenClass: route.name, category });
  };

  return { onReady: record, onStateChange: record };
}

/**
 * Reports a screen view whenever [name] changes.
 *
 * For routers that expose the current screen as a value rather than through a navigator — Expo
 * Router, say:
 *
 * ```tsx
 * useLDScreenView(usePathname());
 * ```
 */
export function useLDScreenView(
  name: string | null | undefined,
  options?: LDScreenViewOptions
): void {
  // Read through a ref so a fresh options object on each render does not re-report the screen.
  const optionsRef = useRef(options);
  optionsRef.current = options;

  useEffect(() => {
    if (name) {
      trackScreenView(name, optionsRef.current);
    }
  }, [name]);
}
