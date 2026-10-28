import type * as ScreenViewModule from '../screenView';
import type * as IndexModule from '../index';

jest.mock('../NativeSessionReplayReactNative', () => ({
  configure: jest.fn().mockResolvedValue(undefined),
  initializeSessionReplay: jest.fn().mockResolvedValue(undefined),
  startSessionReplay: jest.fn().mockResolvedValue(undefined),
  stopSessionReplay: jest.fn().mockResolvedValue(undefined),
  afterIdentify: jest.fn().mockResolvedValue(undefined),
  trackScreenView: jest.fn().mockResolvedValue(undefined),
}));

// Whether native has started is module state, and the buffering only shows up in the transition,
// so every test gets a fresh module registry rather than a shared one.
function loadModules(): {
  native: { trackScreenView: jest.Mock };
  screenView: typeof ScreenViewModule;
  index: typeof IndexModule;
} {
  jest.resetModules();
  return {
    native: require('../NativeSessionReplayReactNative'),
    screenView: require('../screenView'),
    index: require('../index'),
  };
}

const flushAsync = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('trackScreenView', () => {
  it('holds a screen view reported before native session replay started', async () => {
    const { native, screenView, index } = loadModules();

    screenView.trackScreenView('Home');
    expect(native.trackScreenView).not.toHaveBeenCalled();

    await index.initializeSessionReplay();

    expect(native.trackScreenView).toHaveBeenCalledWith('Home', {});
  });

  it('keeps only the most recent screen view while waiting', async () => {
    const { native, screenView, index } = loadModules();

    screenView.trackScreenView('Home');
    screenView.trackScreenView('Settings');
    await index.initializeSessionReplay();

    // The screen the user ended up on is what the opening Navigate should name.
    expect(native.trackScreenView).toHaveBeenCalledTimes(1);
    expect(native.trackScreenView).toHaveBeenCalledWith('Settings', {});
  });

  it('forwards straight through once started', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();

    screenView.trackScreenView('Home');
    screenView.trackScreenView('Settings');

    expect(native.trackScreenView).toHaveBeenNthCalledWith(1, 'Home', {});
    expect(native.trackScreenView).toHaveBeenNthCalledWith(2, 'Settings', {});
  });

  it('releases the buffer on a deferred start too', async () => {
    const { native, screenView, index } = loadModules();

    screenView.trackScreenView('Home');
    await index.startSessionReplay();

    expect(native.trackScreenView).toHaveBeenCalledWith('Home', {});
  });

  it('forwards the optional classifiers and properties', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();

    screenView.trackScreenView('Checkout', {
      screenClass: 'CheckoutScreen',
      screenId: 'checkout',
      category: 'Commerce',
      properties: { step: 2 },
    });

    expect(native.trackScreenView).toHaveBeenCalledWith('Checkout', {
      screenClass: 'CheckoutScreen',
      screenId: 'checkout',
      category: 'Commerce',
      properties: { step: 2 },
    });
  });

  it('omits classifiers that were not supplied', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();

    screenView.trackScreenView('Home', { category: 'navigation' });

    // Absent keys let the native SDK apply its own defaults.
    expect(native.trackScreenView).toHaveBeenCalledWith('Home', {
      category: 'navigation',
    });
  });

  it('ignores a blank name', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();

    screenView.trackScreenView('');
    screenView.trackScreenView('   ');

    expect(native.trackScreenView).not.toHaveBeenCalled();
  });

  it('trims the name', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();

    screenView.trackScreenView('  Home  ');

    expect(native.trackScreenView).toHaveBeenCalledWith('Home', {});
  });

  it('does not throw when native rejects', async () => {
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();
    native.trackScreenView.mockRejectedValueOnce(new Error('native boom'));
    const error = jest.spyOn(console, 'error').mockImplementation(() => {});

    try {
      expect(() => screenView.trackScreenView('Home')).not.toThrow();
      await flushAsync();
      expect(error).toHaveBeenCalled();
    } finally {
      error.mockRestore();
    }
  });

  it('no-ops against a native binary without screen view support', async () => {
    // A JS-only (OTA) update can run this bundle against an older native binary.
    const { native, screenView, index } = loadModules();
    await index.initializeSessionReplay();
    const nativeRecord = native as unknown as Record<string, unknown>;
    const trackScreenView = nativeRecord.trackScreenView;
    delete nativeRecord.trackScreenView;
    const warn = jest.spyOn(console, 'warn').mockImplementation(() => {});

    try {
      expect(() => screenView.trackScreenView('Home')).not.toThrow();
      expect(warn).toHaveBeenCalledTimes(1);

      // The warning is one-shot, not one per navigation.
      screenView.trackScreenView('Settings');
      expect(warn).toHaveBeenCalledTimes(1);
    } finally {
      warn.mockRestore();
      nativeRecord.trackScreenView = trackScreenView;
    }
  });
});
