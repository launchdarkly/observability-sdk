import commonjs from '@rollup/plugin-commonjs'
import json from '@rollup/plugin-json'
import resolve from '@rollup/plugin-node-resolve'
import terser from '@rollup/plugin-terser'
import typescript from '@rollup/plugin-typescript'

/** @type {import('rollup').RollupOptions} */
const config = {
	input: 'src/index.ts',
	context: 'global',
	external: [
		'require-in-the-middle',
		'@launchdarkly/node-server-sdk',
		'@launchdarkly/node-server-sdk-otel',
	],
	plugins: [
		json(),
		commonjs({
			// required for @opentelemetry/resources which pretends to be an ESM build while using dynamic `require()`
			transformMixedEsModules: true,
		}),
		resolve({
			preferBuiltins: true,
			// avoid bundling require-in-the-middle for next.js compatibility
			resolveOnly: (module) =>
				!module.includes('require-in-the-middle') &&
				!module.includes('@launchdarkly/node-server-sdk') &&
				!module.includes('@launchdarkly/node-server-sdk-otel'),
		}),
		typescript(),
		terser(),
	],
	output: [
		{
			file: 'dist/index.js',
			format: 'es',
			sourcemap: true,
			exports: 'auto',
			inlineDynamicImports: true,
		},
		{
			file: 'dist/index.cjs',
			format: 'cjs',
			sourcemap: true,
			exports: 'auto',
			inlineDynamicImports: true,
		},
	],
	treeshake: 'safest',
}

export default config
