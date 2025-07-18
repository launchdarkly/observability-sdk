import { defineConfig } from 'vitest/config'
import path from 'path'

export default defineConfig({
	test: {
		environment: 'jsdom',
		globals: true,
		setupFiles: ['./vitest.setup.ts'],
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
