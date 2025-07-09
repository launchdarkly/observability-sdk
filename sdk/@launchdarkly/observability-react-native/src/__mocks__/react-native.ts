export const Platform = {
	OS: 'ios' as const,
	select: (config: any) => config.ios || config.default,
}

export const AppState = {
	currentState: 'active',
	addEventListener: vi.fn(),
	removeEventListener: vi.fn(),
}

export const NativeModules = {}

export const DeviceEventEmitter = {
	addListener: vi.fn(),
	removeListener: vi.fn(),
}

export const NativeEventEmitter = vi.fn().mockImplementation(() => ({
	addListener: vi.fn(),
	removeListener: vi.fn(),
}))

export default {
	Platform,
	AppState,
	NativeModules,
	DeviceEventEmitter,
	NativeEventEmitter,
}
