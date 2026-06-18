import json from '@rollup/plugin-json'
import terser from '@rollup/plugin-terser'
import typescript from '@rollup/plugin-typescript'

/** @type {import('rollup').RollupOptions} */
const config = {
	input: [
		'src/next-client.tsx',
		'src/config.ts',
		'src/server.ts',
		'src/server.edge.ts',
		'src/ssr.tsx',
	],
	external: [
		'@launchdarkly/observability',
		'@launchdarkly/observability-node',
		'@launchdarkly/session-replay',
		'@opentelemetry/api',
		'@opentelemetry/semantic-conventions',
		'js-cookie',
		'next',
		'next/error.js',
		'next/headers',
		'react',
		'react/jsx-runtime',
	],
	plugins: [json(), typescript(), terser()],
	output: [
		{
			dir: 'dist',
			format: 'cjs',
			sourcemap: true,
			entryFileNames: '[name].cjs',
			exports: 'auto',
		},
		{
			dir: 'dist',
			format: 'es',
			sourcemap: true,
			entryFileNames: '[name].js',
			exports: 'auto',
		},
	],
	treeshake: 'smallest',
}

export default config
