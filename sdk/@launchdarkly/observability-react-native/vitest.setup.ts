import { vi } from 'vitest'

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
