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
		'import-in-the-middle',
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
			// avoid bundling require-in-the-middle for next.js compatibility.
			// import-in-the-middle must not be bundled either: its loader hook
			// (registered by `register.mjs`) and the instrumentations' `Hook`
			// calls must share the same copy of the package.
			resolveOnly: (module) =>
				!module.includes('require-in-the-middle') &&
				!module.includes('import-in-the-middle') &&
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

/**
 * `@launchdarkly/observability-node/register`, preloaded with `node --import`
 * to instrument ES modules. It loads the main CommonJS bundle at runtime so the
 * application and the hook share one set of instrumentations.
 * @type {import('rollup').RollupOptions}
 */
const registerConfig = {
	input: 'src/register.ts',
	external: ['import-in-the-middle', './index.cjs', /^node:/],
	plugins: [
		typescript({
			declaration: false,
			declarationMap: false,
			declarationDir: undefined,
			include: ['src/register.ts'],
		}),
	],
	output: {
		file: 'dist/register.mjs',
		format: 'es',
		sourcemap: true,
	},
}

export default [config, registerConfig]
