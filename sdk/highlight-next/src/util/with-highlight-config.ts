import { NextConfig } from 'next'
import { Rewrite } from 'next/dist/lib/load-custom-routes'
import { WebpackConfigContext } from 'next/dist/server/config-shared'
import HighlightWebpackPlugin from './highlight-webpack-plugin.js'

// Packages that use require-in-the-middle / import-in-the-middle, which
// webpack cannot statically analyze. Marking them external tells Next to load
// them via Node's runtime require instead of bundling them, which silences the
// "Critical dependency" warnings during `next build`.
const OTEL_SERVER_EXTERNAL_PACKAGES = [
	'@highlight-run/node',
	'@opentelemetry/api',
	'@opentelemetry/auto-instrumentations-node',
	'@opentelemetry/instrumentation',
	'@opentelemetry/sdk-node',
	'@prisma/instrumentation',
	'import-in-the-middle',
	'require-in-the-middle',
]

interface HighlightConfigOptionsDefault {
	uploadSourceMaps: boolean
	configureHighlightProxy: boolean
	apiKey: string
	appVersion: string
	environment: string
	serviceName: string
	sourceMapsPath: string
	sourceMapsBasePath: string
	sourceMapsBackendUrl?: string
}

export interface HighlightConfigOptions {
	/**
	 * Explicitly enable or disable source map uploading during production builds.
	 * By default, source maps are uploaded if both:
	 * 1. The NextConfig.productionBrowserSourceMaps is not true
	 * 2. An API key is set through the apiKey option
	 * or HIGHLIGHT_SOURCEMAP_UPLOAD_API_KEY environment variable
	 */
	uploadSourceMaps?: boolean
	/**
	 * Configures a rewrite at /highlight-events for proxying Highlight requests.
	 * @default true
	 */
	configureHighlightProxy?: boolean
	/**
	 * API key used to link to your Highlight project when uploading source maps.
	 * This can also be set through the HIGHLIGHT_SOURCEMAP_UPLOAD_API_KEY environment variable.
	 */
	apiKey?: string
	/**
	 * App version used when uploading source maps.
	 */
	appVersion?: string
	/**
	 * Specifies the environment your application is running in.
	 * This is useful to distinguish whether your session was recorded on localhost or in production.
	 */
	environment?: string
	/**
	 * Name of your app.
	 */
	serviceName?: string
	/**
	 * File system root directory containing all your source map files.
	 * @default '.next/'
	 */
	sourceMapsPath?: string
	/**
	 * Base path to append to your source map URLs when uploaded to Highlight.
	 * @default '_next/'
	 */
	sourceMapsBasePath?: string
	/**
	 * Optional, backend url for private graph to use for uploading (for self-hosted highlight deployments).
	 */
	sourceMapsBackendUrl?: string
}

const getDefaultOpts = async (
	config: NextConfig,
	highlightOpts?: HighlightConfigOptions,
): Promise<HighlightConfigOptionsDefault> => {
	const isProdBuild = process.env.NODE_ENV === 'production'
	const hasSourcemapApiKey =
		!!process.env.HIGHLIGHT_SOURCEMAP_UPLOAD_API_KEY ||
		!!highlightOpts?.apiKey
	let version: string | null = null
	if (config.generateBuildId) {
		const buildId = config.generateBuildId()
		if (typeof buildId === 'string') {
			version = buildId
		} else {
			version = await buildId
		}
	}

	return {
		// upload source maps even if config.productionBrowserSourceMaps is set to upload server maps
		uploadSourceMaps:
			isProdBuild &&
			(highlightOpts?.uploadSourceMaps ?? hasSourcemapApiKey),
		configureHighlightProxy: highlightOpts?.configureHighlightProxy ?? true,
		apiKey: highlightOpts?.apiKey ?? '',
		appVersion: highlightOpts?.appVersion ?? version ?? '',
		environment: highlightOpts?.environment ?? '',
		serviceName: highlightOpts?.serviceName ?? '',
		sourceMapsPath: highlightOpts?.sourceMapsPath ?? '.next/',
		sourceMapsBasePath: highlightOpts?.sourceMapsBasePath ?? '_next/',
		sourceMapsBackendUrl: highlightOpts?.sourceMapsBackendUrl,
	}
}

type NextConfigObject = NextConfig
type NextConfigFunction = (
	phase: string,
	{ defaultConfig }: { defaultConfig: any },
) => NextConfig
type NextConfigAsyncFunction = (
	phase: string,
	{ defaultConfig }: { defaultConfig: any },
) => Promise<NextConfig>
type NextConfigInput =
	| NextConfigObject
	| NextConfigFunction
	| NextConfigAsyncFunction

const getHighlightConfig = async (
	config: NextConfig,
	highlightOpts?: HighlightConfigOptions,
) => {
	const defaultOpts = await getDefaultOpts(config, highlightOpts)

	let newRewrites = config.rewrites
	if (defaultOpts.uploadSourceMaps || defaultOpts.configureHighlightProxy) {
		newRewrites = async () => {
			let re:
				| Rewrite[]
				| {
						beforeFiles?: Rewrite[]
						afterFiles?: Rewrite[]
						fallback?: Rewrite[]
				  }
			if (!config.rewrites) {
				re = new Array<Rewrite>()
			} else {
				re = await config.rewrites()
			}

			const sourcemapRewrite = {
				source: '/:path*.map',
				destination: '/404',
			}

			const highlightRewrites = [
				{
					source: '/highlight-events',
					destination: 'https://pub.highlight.io',
				},
				{
					source: '/v1/traces',
					destination: 'https://otel.highlight.io/v1/traces',
				},
				{
					source: '/v1/metrics',
					destination: 'https://otel.highlight.io/v1/metrics',
				},
				{
					source: '/v1/logs',
					destination: 'https://otel.highlight.io/v1/logs',
				},
			]

			if (!re || Array.isArray(re)) {
				re = re ?? []
				if (defaultOpts.uploadSourceMaps) {
					re.push(sourcemapRewrite)
				}
				if (defaultOpts.configureHighlightProxy) {
					re = re.concat(...highlightRewrites)
				}
				return re
			} else {
				return {
					beforeFiles: defaultOpts.uploadSourceMaps
						? (re.beforeFiles ?? []).concat(sourcemapRewrite)
						: (re.beforeFiles ?? []),
					afterFiles: defaultOpts.configureHighlightProxy
						? (re.afterFiles ?? []).concat(...highlightRewrites)
						: (re.afterFiles ?? []),
					fallback: re.fallback ?? [],
				}
			}
		}
	}

	const newWebpack = (webpackConfig: any, opts: WebpackConfigContext) => {
		let originalConfig = webpackConfig
		if (config.webpack) {
			originalConfig = config.webpack(webpackConfig, opts)
		}

		if (opts.isServer) {
			if (defaultOpts.uploadSourceMaps) {
				originalConfig.devtool = 'source-map'
			}

			// Defense-in-depth: even with serverExternalPackages set, some
			// builds (custom resolvers, monorepo hoisting) can still pull
			// these modules through webpack. Silence the known-noisy
			// warnings rather than letting them spam the customer build.
			const criticalDepMessage =
				/Critical dependency: the request of a dependency is an expression/
			originalConfig.ignoreWarnings = [
				...(originalConfig.ignoreWarnings ?? []),
				{
					module: /node_modules\/(@opentelemetry\/instrumentation|require-in-the-middle|import-in-the-middle|@highlight-run\/node|@prisma\/instrumentation)/,
					message: criticalDepMessage,
				},
				{
					// Workspace / hoisted builds where @highlight-run/node
					// is symlinked outside node_modules.
					module: /highlight-node\/dist/,
					message: criticalDepMessage,
				},
			]
		}

		if (defaultOpts.uploadSourceMaps) {
			originalConfig.plugins.push(
				new HighlightWebpackPlugin(
					defaultOpts.apiKey,
					defaultOpts.appVersion,
					defaultOpts.sourceMapsPath,
					defaultOpts.sourceMapsBasePath,
					defaultOpts.sourceMapsBackendUrl,
				),
			)
		}

		return originalConfig
	}

	const existingServerExternal = Array.isArray(config.serverExternalPackages)
		? config.serverExternalPackages
		: []
	const serverExternalPackages = Array.from(
		new Set([...existingServerExternal, ...OTEL_SERVER_EXTERNAL_PACKAGES]),
	)

	return {
		...config,
		env: {
			...config.env,
			configureHighlightProxy: defaultOpts.configureHighlightProxy
				? 'true'
				: '',
		},
		productionBrowserSourceMaps:
			defaultOpts.uploadSourceMaps || config.productionBrowserSourceMaps,
		rewrites: newRewrites,
		webpack: newWebpack,
		serverExternalPackages,
	}
}

export async function withHighlightConfig(
	config: NextConfigObject,
	highlightOpts?: HighlightConfigOptions,
): Promise<NextConfig>
export async function withHighlightConfig(
	config: NextConfigFunction | NextConfigAsyncFunction,
	highlightOpts?: HighlightConfigOptions,
): Promise<NextConfigAsyncFunction>
export async function withHighlightConfig(
	config: NextConfigInput,
	highlightOpts?: HighlightConfigOptions,
): Promise<NextConfig | NextConfigAsyncFunction> {
	if (typeof config === 'function') {
		const phaseHandler: NextConfigAsyncFunction = async (
			phase: string,
			{ defaultConfig }: { defaultConfig: any },
		): Promise<NextConfig> => {
			const userNextConfigObject: NextConfig | Promise<NextConfig> =
				config(phase, { defaultConfig })
			if (typeof userNextConfigObject === 'function') {
				const nc = await (userNextConfigObject as Promise<NextConfig>)
				return await getHighlightConfig(nc, highlightOpts)
			} else {
				return await getHighlightConfig(
					userNextConfigObject as NextConfig,
					highlightOpts,
				)
			}
		}
		return phaseHandler
	} else {
		return await getHighlightConfig(config, highlightOpts)
	}
}
