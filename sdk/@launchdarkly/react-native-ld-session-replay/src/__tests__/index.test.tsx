import NativeSessionReplayReactNative from '../NativeSessionReplayReactNative'
import { configureSessionReplay, createSessionReplayPlugin } from '../index'

jest.mock('../NativeSessionReplayReactNative', () => ({
  configure: jest.fn().mockResolvedValue(undefined),
  startSessionReplay: jest.fn().mockResolvedValue(undefined),
  stopSessionReplay: jest.fn().mockResolvedValue(undefined),
}))

describe('configureSessionReplay', () => {
  it('rejects if key is empty', async () => {
    await expect(configureSessionReplay('')).rejects.toThrow()
  })

  it('rejects if key is whitespace', async () => {
    await expect(configureSessionReplay('   ')).rejects.toThrow()
  })
})

describe('SessionReplayPluginAdapter', () => {
  it('calls configure and startSessionReplay on register', async () => {
    const plugin = createSessionReplayPlugin()
    plugin.register({}, { sdk: { name: 'test', version: '0.0.0' }, mobileKey: 'mob-key-123' })

    await new Promise(process.nextTick)

    expect(NativeSessionReplayReactNative.configure).toHaveBeenCalledWith(
      'mob-key-123',
      {}
    )
    expect(NativeSessionReplayReactNative.startSessionReplay).toHaveBeenCalled()
  })
})
