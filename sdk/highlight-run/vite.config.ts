// vite.config.ts
import commonjs from '@rollup/plugin-commonjs'
import resolve from '@rollup/plugin-node-resolve'
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import dts from 'vite-plugin-dts'

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
			// Inline the types of these dependencies into the rolled-up
			// declaration file. They are bundled into the JS output (or are
			// workspace packages) and are NOT declared as runtime dependencies,
			// so without bundling, their `.d.ts` imports leak into the published
			// types as unresolvable bare specifiers and break consumer
			// type-checking when `skipLibCheck` is off (TypeScript's default).
			//
			// Intentionally NOT bundled:
			//   - `@launchdarkly/js-client-sdk`: a real runtime dependency, so
			//     its types resolve via the consumer's node_modules.
			//   - `stacktrace-js`: api-extractor cannot follow its `StackFrame`
			//     symbol ("Unable to follow symbol"), so it is declared as a
			//     runtime dependency instead (see package.json).
			//   - `graphql-request` / `graphql`: not bundled because their own
			//     declarations are not self-contained (they pull in `graphql`,
			//     `cross-fetch`, ...). Instead these types are kept out of the
			//     public surface entirely — the internal `graphqlSDK` fields
			//     that referenced them are now ECMAScript-private (`#`).
			bundledPackages: [
				'@opentelemetry/api',
				'@highlight-run/rrweb-types',
			],
		}),
	],
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
						},
			name: 'LD',
			fileName: (format, entryName) =>
				format === 'es'
					? `${entryName}.js`
					: `${entryName}.${format}.js`,
		},
		minify: true,
		sourcemap: true,
		emptyOutDir: false,
		rollupOptions: {
			treeshake: 'smallest',
			plugins: [
				commonjs({
					transformMixedEsModules: true,
				}),
				resolve({
					browser: true,
				}),
			],
			output: {
				exports: 'named',
			},
			cache: false,
		},
	},
})
