// vite.config.ts
//
// `highlight.run` is a thin wrapper that bundles `@launchdarkly/o11y` into a
// self-contained package, mirroring how `@launchdarkly/observability` and
// `@launchdarkly/session-replay` are built. It keeps every entry point that
// `highlight.run` has always shipped (`.`, `./observe`, `./record`,
// `./ld/observe`, `./ld/record`) plus the `index.umd.js` browser bundle.
//
// UMD does not support multiple entries, so `build` runs twice:
//   FORMAT=umd vite build   -> dist/index.umd.js
//   vite build              -> dist/{index,observe,record,LDObserve,LDRecord}.js
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import { visualizer } from 'rollup-plugin-visualizer'

const umd = process.env.FORMAT === 'umd'

export default defineConfig(({}) => {
	return {
		build: {
			target: 'es6',
			lib: {
				name: 'LD',
				formats: umd ? ['umd'] : ['es'],
				entry: umd
					? resolvePath(__dirname, 'src/index.ts')
					: {
							index: resolvePath(__dirname, 'src/index.ts'),
							observe: resolvePath(__dirname, 'src/observe.ts'),
							record: resolvePath(__dirname, 'src/record.ts'),
							LDObserve: resolvePath(
								__dirname,
								'src/LDObserve.ts',
							),
							LDRecord: resolvePath(__dirname, 'src/LDRecord.ts'),
						},
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
				output: {
					exports: 'named',
				},
				cache: false,
			},
		},
		plugins:
			process.env.VISUALIZE_BUNDLE === 'true'
				? [
						visualizer({
							gzipSize: true,
							brotliSize: true,
							sourcemap: true,
							open: true,
						}),
					]
				: [],
	}
})
