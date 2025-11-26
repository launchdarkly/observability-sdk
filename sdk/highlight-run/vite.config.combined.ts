// vite.config.combined.ts
// Build configuration for testing combined bundle size
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig({
	build: {
		target: 'es2020',
		lib: {
			formats: ['es', 'umd'],
			entry: resolvePath(__dirname, 'test-combined-bundle.ts'),
			name: 'LDObservabilityBundle',
			fileName: (format) => `ld-combined.${format}.js`,
		},
		minify: 'terser',
		terserOptions: {
			ecma: 2020,
			module: true,
			toplevel: true,
			compress: {
				ecma: 2020,
				module: true,
				toplevel: true,
				unsafe_arrows: true,
				drop_console: true,
				drop_debugger: true,
				passes: 3,
				pure_getters: true,
				unsafe: true,
				unsafe_comps: true,
				unsafe_math: true,
				unsafe_methods: true,
				unsafe_proto: true,
				unsafe_regexp: true,
				unsafe_undefined: true,
				unused: true,
				dead_code: true,
				inline: 3,
				side_effects: false,
			},
			mangle: {
				toplevel: true,
			},
			format: {
				comments: false,
				ecma: 2020,
			},
		},
		rollupOptions: {
			treeshake: {
				moduleSideEffects: false,
				propertyReadSideEffects: false,
				tryCatchDeoptimization: false,
				unknownGlobalSideEffects: false,
			},
			output: {
				compact: true,
				minifyInternalExports: true,
			},
			plugins: process.env.ANALYZE === 'true' ? [
				visualizer({
					filename: 'dist/combined-bundle-stats.html',
					open: true,
					gzipSize: true,
					brotliSize: true,
				}) as any,
			] : [],
		},
	},
})