// vite.config.ts
import commonjs from '@rollup/plugin-commonjs'
import json from '@rollup/plugin-json'
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
				json(),
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
	test: {
		environment: 'jsdom',
	},
})
