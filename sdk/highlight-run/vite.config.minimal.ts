// vite.config.minimal.ts
// O11Y-393: Ultra-minimal build configuration targeting <100KB bundle
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import dts from 'vite-plugin-dts'

export default defineConfig({
	envPrefix: ['REACT_APP_'],
	plugins: [
		dts({
			declarationOnly: process.env.FORMAT === 'd.ts',
			rollupTypes: true,
			strictOutput: true,
		}),
	],
	build: {
		target: 'es2020', // Use modern JS features for smaller output
		lib: {
			formats: ['es'],
			entry: {
				// Ultra-minimal LaunchDarkly plugins (no rrweb)
				'ld-ultra-minimal': resolvePath(__dirname, 'src/sdk/ld-ultra-minimal.ts'),
			},
			fileName: (format, entryName) => `${entryName}.js`,
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
				pure_funcs: ['console.log', 'console.debug', 'console.info'],
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
				// Remove all side effects from unused code
				side_effects: false,
			},
			mangle: {
				toplevel: true,
				properties: {
					// Mangle all private properties
					regex: /^_/,
					// Also mangle common property names
					reserved: ['observe', 'record', 'init']
				},
			},
			format: {
				comments: false,
				ecma: 2020,
			},
		},
		sourcemap: false, // No sourcemaps for minimal build
		rollupOptions: {
			treeshake: {
				moduleSideEffects: false,
				propertyReadSideEffects: false,
				tryCatchDeoptimization: false,
				unknownGlobalSideEffects: false,
				// Aggressive tree shaking
				annotations: false,
				correctVarValueBeforeDeclaration: false,
			},
			output: {
				compact: true,
				minifyInternalExports: true,
				generatedCode: {
					arrowFunctions: true,
					constBindings: true,
					objectShorthand: true,
					symbols: true,
				},
			},
			// External dependencies for minimal build
			external: [
				// Make OpenTelemetry completely external/optional
				/@opentelemetry/,
				// Make heavy deps external
				'web-vitals',
				'zone.js',
				'graphql',
				'graphql-request',
				'graphql-tag',
				// Optional integrations
				'@amplitude/analytics-browser',
				'mixpanel-browser',
			],
		},
	},
})