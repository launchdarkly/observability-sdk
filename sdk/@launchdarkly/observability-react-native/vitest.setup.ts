import { vi } from 'vitest'

// Mock AsyncStorage
vi.mock('@react-native-async-storage/async-storage', () => ({
	default: {
		getItem: vi.fn().mockResolvedValue(null),
		setItem: vi.fn().mockResolvedValue(undefined),
		removeItem: vi.fn().mockResolvedValue(undefined),
		clear: vi.fn().mockResolvedValue(undefined),
	},
}))

// Mock react-native-device-info
vi.mock('react-native-device-info', () => ({
	default: {
		getUniqueId: vi.fn().mockResolvedValue('test-device-id'),
		getVersion: vi.fn().mockReturnValue('1.0.0'),
	},
}))

// Mock expo-constants
vi.mock('expo-constants', () => ({
	default: {
		expoConfig: {
			name: 'test-app',
			version: '1.0.0',
		},
	},
}))

// Global test utilities
global.console = {
	...console,
	log: vi.fn(),
	warn: vi.fn(),
	error: vi.fn(),
}
