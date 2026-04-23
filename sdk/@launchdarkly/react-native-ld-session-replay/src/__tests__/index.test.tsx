import NativeSessionReplayReactNative from '../NativeSessionReplayReactNative';
import {
  afterIdentify,
  configureSessionReplay,
  createSessionReplayPlugin,
} from '../index';

jest.mock('../NativeSessionReplayReactNative', () => ({
  configure: jest.fn().mockResolvedValue(undefined),
  startSessionReplay: jest.fn().mockResolvedValue(undefined),
  stopSessionReplay: jest.fn().mockResolvedValue(undefined),
  afterIdentify: jest.fn().mockResolvedValue(undefined),
}));

describe('configureSessionReplay', () => {
  it('rejects if key is empty', async () => {
    await expect(configureSessionReplay('')).rejects.toThrow();
  });

  it('rejects if key is whitespace', async () => {
    await expect(configureSessionReplay('   ')).rejects.toThrow();
  });
});

describe('afterIdentify', () => {
  it('passes kind and key for a single-kind context', async () => {
    await afterIdentify({ kind: 'user', key: 'abc' }, true);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'abc' },
      'abc',
      true
    );
  });

  it('uses kind:key canonical key for non-user single-kind context', async () => {
    await afterIdentify({ kind: 'org', key: 'acme' }, true);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { org: 'acme' },
      'org:acme',
      true
    );
  });

  it('escapes colons and percent signs in keys', async () => {
    await afterIdentify({ kind: 'org', key: 'a:b%c' }, true);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { org: 'a:b%c' },
      'org:a%3Ab%25c',
      true
    );
  });

  it('passes all sub-context kind/key pairs for a multi-kind context', async () => {
    await afterIdentify(
      { kind: 'multi', org: { key: 'acme' }, user: { key: 'abc' } },
      true
    );
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { org: 'acme', user: 'abc' },
      'org:acme:user:abc',
      true
    );
  });

  it('sorts sub-contexts by kind for the canonical key', async () => {
    await afterIdentify(
      { kind: 'multi', user: { key: 'abc' }, org: { key: 'acme' } },
      true
    );
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'abc', org: 'acme' },
      'org:acme:user:abc',
      true
    );
  });

  it('sorts by kind name, not by kind:key string', async () => {
    // "org-team" sorts before "org" when sorting full "kind:key" strings because
    // '-' (45) < ':' (58). Sorting by kind name only keeps "org" first.
    await afterIdentify(
      { 'kind': 'multi', 'org-team': { key: 'eng' }, 'org': { key: 'acme' } },
      true
    );
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { 'org-team': 'eng', 'org': 'acme' },
      'org:acme:org-team:eng',
      true
    );
  });

  it('handles legacy LDUser with implicit user kind', async () => {
    await afterIdentify({ key: 'legacy-user' }, true);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'legacy-user' },
      'legacy-user',
      true
    );
  });

  it('passes completed=false through', async () => {
    await afterIdentify({ kind: 'user', key: 'abc' }, false);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'abc' },
      'abc',
      false
    );
  });
});

describe('SessionReplayPluginAdapter', () => {
  it('returns a hook from getHooks', () => {
    const plugin = createSessionReplayPlugin();
    const hooks = plugin.getHooks!({
      sdk: { name: 'test', version: '0.0.0' },
      mobileKey: 'mob-key-123',
    });
    expect(hooks).toHaveLength(1);
    expect(hooks[0]!.getMetadata().name).toBe('session-replay-react-native');
  });

  it('hook afterIdentify calls native afterIdentify with context', async () => {
    const plugin = createSessionReplayPlugin();
    const [hook] = plugin.getHooks!({
      sdk: { name: 'test', version: '0.0.0' },
      mobileKey: 'mob-key-123',
    });
    hook!.afterIdentify!(
      { context: { kind: 'user', key: 'abc' } },
      {},
      { status: 'completed' }
    );
    await new Promise(process.nextTick);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'abc' },
      'abc',
      true
    );
  });

  it('hook afterIdentify passes completed=false for shed status', async () => {
    const plugin = createSessionReplayPlugin();
    const [hook] = plugin.getHooks!({
      sdk: { name: 'test', version: '0.0.0' },
      mobileKey: 'mob-key-123',
    });
    hook!.afterIdentify!(
      { context: { kind: 'user', key: 'abc' } },
      {},
      { status: 'shed' }
    );
    await new Promise(process.nextTick);
    expect(NativeSessionReplayReactNative.afterIdentify).toHaveBeenCalledWith(
      { user: 'abc' },
      'abc',
      false
    );
  });

  it('calls configure and startSessionReplay on register', async () => {
    const plugin = createSessionReplayPlugin();
    plugin.register(
      {},
      { sdk: { name: 'test', version: '0.0.0' }, mobileKey: 'mob-key-123' }
    );

    await new Promise(process.nextTick);

    expect(NativeSessionReplayReactNative.configure).toHaveBeenCalledWith(
      'mob-key-123',
      {}
    );
    expect(
      NativeSessionReplayReactNative.startSessionReplay
    ).toHaveBeenCalled();
  });
});
