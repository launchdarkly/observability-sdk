// vite.config.ts
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig(({}) => {
	return {
		build: {
			target: 'esnext',
			lib: {
				name: 'Observability',
				formats: ['umd', 'es'],
				entry: resolvePath(__dirname, 'src/index.ts'),
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
				output: [
					{ format: 'es', exports: 'named' },
					{
						format: 'umd',
						name: 'Observability',
						exports: 'named',
					},
				],
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
