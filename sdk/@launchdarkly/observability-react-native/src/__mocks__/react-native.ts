import { type Mock, vi } from 'vitest'

export const Platform = {
	OS: 'ios' as const,
	select: (config: any) => config.ios || config.default,
}

export const AppState: {
	currentState: string
	addEventListener: Mock
	removeEventListener: Mock
} = {
	currentState: 'active',
	addEventListener: vi.fn(),
	removeEventListener: vi.fn(),
}

export const NativeModules = {}

export const DeviceEventEmitter: {
	addListener: Mock
	removeListener: Mock
} = {
	addListener: vi.fn(),
	removeListener: vi.fn(),
}

export const NativeEventEmitter: Mock = vi.fn().mockImplementation(() => ({
	addListener: vi.fn(),
	removeListener: vi.fn(),
}))

export default {
	Platform,
	AppState,
	NativeModules,
	DeviceEventEmitter,
	NativeEventEmitter,
} as const

// Mock global ErrorUtils (React Native global)
;(globalThis as any).ErrorUtils = {
	getGlobalHandler: vi.fn(() => undefined),
	setGlobalHandler: vi.fn(),
}
