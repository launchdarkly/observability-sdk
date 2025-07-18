import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import dts from 'vite-plugin-dts'

export default defineConfig({
	plugins: [dts()],
	build: {
		target: 'es2020',
		lib: {
			formats: ['es', 'cjs'],
			entry: resolvePath(__dirname, 'src/index.ts'),
			name: 'LDObservabilityReactNative',
			fileName: (format) => `index.${format === 'es' ? 'js' : 'cjs'}`,
		},
		minify: true,
		sourcemap: true,
		emptyOutDir: true,
		rollupOptions: {
			treeshake: 'smallest',
			output: {
				exports: 'named',
			},
			external: ['@launchdarkly/react-native-client-sdk', 'react-native'],
		},
	},
})
