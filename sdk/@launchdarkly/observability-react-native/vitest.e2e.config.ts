import { defineConfig } from 'vitest/config'
import path from 'path'

// Dedicated vitest config for e2e validation tests.
// Picks up `*.e2e-test.ts` files instead of the default `*.test.ts`.
export default defineConfig({
	test: {
		environment: 'jsdom',
		globals: true,
		setupFiles: ['./vitest.setup.ts'],
		include: ['**/*.e2e-test.ts'],
		testTimeout: 240_000,
		hookTimeout: 60_000,
		typecheck: {
			tsconfig: './tsconfig.json',
		},
	},
	resolve: {
		alias: {
			'react-native': path.resolve(
				__dirname,
				'./src/__mocks__/react-native.ts',
			),
		},
	},
})
