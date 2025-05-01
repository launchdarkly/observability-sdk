// vite.config.ts
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'

export default defineConfig({
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
})
