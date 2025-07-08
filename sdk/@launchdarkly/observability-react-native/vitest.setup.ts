import { vi } from 'vitest'

vi.mock('expo-constants', () => ({
	default: {
		expoConfig: {
			name: 'test-app',
			version: '1.0.0',
		},
	},
}))

