import NativeSessionReplayReactNative from '../NativeSessionReplayReactNative';
import { initializeSessionReplay } from '../index';
import {
  createNavigationObserver,
  screenNameFromRouteName,
  type LDRoute,
} from '../navigation';

jest.mock('../NativeSessionReplayReactNative', () => ({
  configure: jest.fn().mockResolvedValue(undefined),
  initializeSessionReplay: jest.fn().mockResolvedValue(undefined),
  startSessionReplay: jest.fn().mockResolvedValue(undefined),
  stopSessionReplay: jest.fn().mockResolvedValue(undefined),
  afterIdentify: jest.fn().mockResolvedValue(undefined),
  trackScreenView: jest.fn().mockResolvedValue(undefined),
}));

const nativeTrackScreenView =
  NativeSessionReplayReactNative.trackScreenView as unknown as jest.Mock;

/** A navigation ref whose current route the test drives directly. */
function fakeNavigationRef(): {
  getCurrentRoute: () => LDRoute | undefined;
  current: LDRoute | undefined;
} {
  const ref = {
    current: undefined as LDRoute | undefined,
    getCurrentRoute: () => ref.current,
  };
  return ref;
}

// The observer reports through trackScreenView, which buffers until native has started.
beforeAll(async () => {
  await initializeSessionReplay();
});

beforeEach(() => {
  jest.clearAllMocks();
});

describe('screenNameFromRouteName', () => {
  it('leaves a plain route name untouched', () => {
    expect(screenNameFromRouteName('Checkout')).toBe('Checkout');
  });

  it('does not truncate a name that merely contains punctuation', () => {
    // A route name is free to be a sentence; only path-looking names are treated as URLs.
    expect(screenNameFromRouteName('Delete this?')).toBe('Delete this?');
  });

  it('strips a query string from a path', () => {
    expect(screenNameFromRouteName('/reset?token=abc123')).toBe('/reset');
  });

  it('strips a fragment from a path', () => {
    expect(screenNameFromRouteName('/orders#summary')).toBe('/orders');
  });

  it('drops a trailing slash but keeps the root', () => {
    expect(screenNameFromRouteName('/orders/')).toBe('/orders');
    expect(screenNameFromRouteName('/')).toBe('/');
  });

  it('keeps path parameters, which only the app can collapse', () => {
    expect(screenNameFromRouteName('/orders/42')).toBe('/orders/42');
  });
});

describe('createNavigationObserver', () => {
  it('reports the first screen on ready', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);
    ref.current = { key: 'home-1', name: 'Home' };

    observer.onReady();

    expect(nativeTrackScreenView).toHaveBeenCalledWith('Home', {
      screenClass: 'Home',
    });
  });

  it('reports each navigation', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);

    ref.current = { key: 'home-1', name: 'Home' };
    observer.onReady();
    ref.current = { key: 'settings-1', name: 'Settings' };
    observer.onStateChange();

    expect(nativeTrackScreenView).toHaveBeenNthCalledWith(2, 'Settings', {
      screenClass: 'Settings',
    });
  });

  it('skips a state change that left the same route on top', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);

    ref.current = { key: 'home-1', name: 'Home' };
    observer.onReady();
    // e.g. a param update, or dismissing a modal the extractor skipped
    observer.onStateChange();

    expect(nativeTrackScreenView).toHaveBeenCalledTimes(1);
  });

  it('reports the same screen pushed twice as two navigations', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);

    ref.current = { key: 'order-1', name: 'Order' };
    observer.onReady();
    ref.current = { key: 'order-2', name: 'Order' };
    observer.onStateChange();

    expect(nativeTrackScreenView).toHaveBeenCalledTimes(2);
  });

  it('attaches the configured category', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref, { category: 'navigation' });
    ref.current = { key: 'home-1', name: 'Home' };

    observer.onReady();

    expect(nativeTrackScreenView).toHaveBeenCalledWith('Home', {
      screenClass: 'Home',
      category: 'navigation',
    });
  });

  it('skips routes the extractor declines', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref, {
      screenNameExtractor: (route) =>
        route.name === 'ConfirmDialog' ? null : route.name,
    });

    ref.current = { key: 'home-1', name: 'Home' };
    observer.onReady();
    ref.current = { key: 'dialog-1', name: 'ConfirmDialog' };
    observer.onStateChange();

    expect(nativeTrackScreenView).toHaveBeenCalledTimes(1);
    expect(nativeTrackScreenView).toHaveBeenCalledWith('Home', {
      screenClass: 'Home',
    });
  });

  it('reports the route underneath again after a skipped route is dismissed', () => {
    // The skipped route never became "last recorded", so the route it covered is current
    // again without that being a navigation.
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref, {
      screenNameExtractor: (route) =>
        route.name === 'ConfirmDialog' ? null : route.name,
    });
    const home = { key: 'home-1', name: 'Home' };

    ref.current = home;
    observer.onReady();
    ref.current = { key: 'dialog-1', name: 'ConfirmDialog' };
    observer.onStateChange();
    ref.current = home;
    observer.onStateChange();

    expect(nativeTrackScreenView).toHaveBeenCalledTimes(1);
  });

  it('strips a query string from a path-style route name', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);
    ref.current = { key: 'reset-1', name: '/reset?token=abc123' };

    observer.onReady();

    expect(nativeTrackScreenView).toHaveBeenCalledWith('/reset', {
      screenClass: '/reset?token=abc123',
    });
  });

  it('does nothing before the navigator has a current route', () => {
    const ref = fakeNavigationRef();
    const observer = createNavigationObserver(ref);

    observer.onReady();

    expect(nativeTrackScreenView).not.toHaveBeenCalled();
  });
});
