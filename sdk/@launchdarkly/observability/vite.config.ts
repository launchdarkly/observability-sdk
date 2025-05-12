// vite.config.ts
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'
import { visualizer } from 'rollup-plugin-visualizer'

export default defineConfig(({}) => {
	return {
		build: {
			target: 'esnext',
			lib: {
				formats: ['es'],
				entry: resolvePath(__dirname, 'src/index.ts'),
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
