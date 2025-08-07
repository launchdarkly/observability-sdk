// vite.config.optimized.ts
// O11Y-393: Optimized build configuration for smaller bundle sizes
import commonjs from '@rollup/plugin-commonjs'
import resolve from '@rollup/plugin-node-resolve'
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import dts from 'vite-plugin-dts'
import { visualizer } from 'rollup-plugin-visualizer'
import terser from '@rollup/plugin-terser'

export default defineConfig({
	envPrefix: ['REACT_APP_'],
	server: {
		host: '0.0.0.0',
		port: 8877,
		strictPort: true,
		hmr: {
			clientPort: 8877,
		},
	},
	plugins: [
		dts({
			declarationOnly: process.env.FORMAT === 'd.ts',
			rollupTypes: true,
			strictOutput: true,
		}),
		// Add bundle analyzer in development
		process.env.ANALYZE === 'true' &&
			visualizer({
				filename: 'dist/bundle-stats.html',
				open: true,
				gzipSize: true,
				brotliSize: true,
			}),
	].filter(Boolean),
	build: {
		target: 'es6',
		lib: {
			formats: process.env.FORMAT === 'umd' ? ['umd'] : ['es'],
			entry:
				process.env.FORMAT === 'umd'
					? resolvePath(__dirname, 'src/index.tsx')
					: {
							index: resolvePath(__dirname, 'src/index.tsx'),
							record: resolvePath(
								__dirname,
								'src/plugins/record.ts',
							),
							observe: resolvePath(
								__dirname,
								'src/plugins/observe.ts',
							),
							LDRecord: resolvePath(
								__dirname,
								'src/sdk/LDRecord.ts',
							),
							LDObserve: resolvePath(
								__dirname,
								'src/sdk/LDObserve.ts',
							),
							// Add separate entry for heavy OpenTelemetry features
							'otel-core': resolvePath(
								__dirname,
								'src/client/otel/index.ts',
							),
							'otel-instrumentation': resolvePath(
								__dirname,
								'src/client/otel/user-interaction.ts',
							),
						},
			name: 'LD',
			fileName: (format, entryName) =>
				format === 'es'
					? `${entryName}.js`
					: `${entryName}.${format}.js`,
		},
		minify: 'terser',
		terserOptions: {
			compress: {
				// Remove console logs in production
				drop_console: process.env.NODE_ENV === 'production',
				drop_debugger: true,
				// More aggressive compression
				passes: 3,
				pure_funcs: ['console.log', 'console.info', 'console.debug'],
				// Remove dead code
				dead_code: true,
				// Inline functions where possible
				inline: 2,
			},
			mangle: {
				// Mangle property names for smaller output
				properties: {
					regex: /^_/,
				},
			},
			format: {
				// Remove comments
				comments: false,
			},
		},
		sourcemap: true,
		emptyOutDir: false,
		rollupOptions: {
			// Most aggressive tree shaking
			treeshake: {
				moduleSideEffects: false,
				propertyReadSideEffects: false,
				tryCatchDeoptimization: false,
			},
			plugins: [
				commonjs({
					transformMixedEsModules: true,
					// Only include what's actually used
					include: /node_modules/,
					requireReturnsDefault: 'auto',
				}),
				resolve({
					browser: true,
					preferBuiltins: false,
					// Skip unnecessary modules
					dedupe: [
						'@opentelemetry/api',
						'@opentelemetry/core',
						'@opentelemetry/resources',
					],
				}),
				// Additional minification with terser
				terser({
					compress: {
						ecma: 2015,
						module: true,
						toplevel: true,
						unsafe_arrows: true,
						drop_console: process.env.NODE_ENV === 'production',
						passes: 2,
					},
				}),
			],
			output: {
				exports: 'named',
				// Use compact output format
				compact: true,
				// Manual chunks for better code splitting (not for UMD)
				manualChunks: process.env.FORMAT === 'umd' ? undefined : (id) => {
					// Separate OpenTelemetry into its own chunk
					if (id.includes('@opentelemetry')) {
						// Split OTEL by functionality
						if (id.includes('instrumentation')) {
							return 'otel-instrumentation'
						}
						if (id.includes('exporter')) {
							return 'otel-exporter'
						}
						if (id.includes('sdk-trace')) {
							return 'otel-trace'
						}
						if (id.includes('sdk-metrics')) {
							return 'otel-metrics'
						}
						return 'otel-core'
					}
					// Separate LaunchDarkly SDK
					if (id.includes('@launchdarkly')) {
						return 'launchdarkly'
					}
					// Separate rrweb
					if (id.includes('rrweb')) {
						return 'rrweb'
					}
					// Separate utility libraries
					if (id.includes('fflate')) {
						return 'compression'
					}
					if (id.includes('stacktrace') || id.includes('error-stack')) {
						return 'error-handling'
					}
				},
			},
			// Mark external dependencies for dynamic loading
			external: (id) => {
				// Keep heavy optional dependencies external
				if (process.env.FORMAT !== 'umd') {
					// These can be loaded on-demand
					const optionalDeps = [
						'zone.js',
						'web-vitals',
					]
					return optionalDeps.some(dep => id.includes(dep))
				}
				return false
			},
			cache: false,
		},
	},
	// Optimization settings
	optimizeDeps: {
		include: [
			'@opentelemetry/api',
			'@launchdarkly/js-client-sdk',
		],
		exclude: [
			// Exclude heavy optional dependencies
			'zone.js',
			'web-vitals',
		],
	},
	test: {
		environment: 'jsdom',
	},
})