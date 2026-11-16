import commonjs from '@rollup/plugin-commonjs'
import json from '@rollup/plugin-json'
import resolve from '@rollup/plugin-node-resolve'
import terser from '@rollup/plugin-terser'
import typescript from '@rollup/plugin-typescript'

// Kept out of the bundle and installed as runtime dependencies. The module
// hooks must not be bundled: `register.mjs` installs the import-in-the-middle
// loader hook, and it only reaches instrumentations that use the same copy of
// import-in-the-middle.
const external = [
	'require-in-the-middle',
	'import-in-the-middle',
	'@launchdarkly/node-server-sdk',
	'@launchdarkly/node-server-sdk-otel',
]

/** @type {import('rollup').RollupOptions} */
const config = {
	input: 'src/index.ts',
	context: 'global',
	external,
	plugins: [
		json(),
		commonjs({
			// required for @opentelemetry/resources which pretends to be an ESM build while using dynamic `require()`
			transformMixedEsModules: true,
		}),
		resolve({
			preferBuiltins: true,
			// don't bundle the externals (the module hooks must also stay
			// external for next.js compatibility)
			resolveOnly: (module) =>
				!external.some((name) => module.includes(name)),
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
 * `node --import @launchdarkly/observability-node/register`: registers the
 * import-in-the-middle loader hook, then loads `index.cjs`.
 * @type {import('rollup').RollupOptions}
 */
const registerConfig = {
	input: 'src/register.ts',
	external: ['module', 'import-in-the-middle'],
	plugins: [typescript({ include: ['src/register.ts'] })],
	output: {
		file: 'dist/register.mjs',
		format: 'es',
		sourcemap: true,
	},
}

export default [config, registerConfig]
