import { NextConfig } from 'next'
import { Rewrite } from 'next/dist/lib/load-custom-routes'

import { PROXY_BACKEND_PATH, PROXY_ENV_FLAG, resolveLDEndpoints } from './proxy'

/**
 * Packages that the LaunchDarkly observability node SDK relies on and that must
 * be loaded natively at runtime rather than webpack-bundled. Bundling them
 * breaks `require-in-the-middle`-based instrumentation (and therefore
 * server-side tracing initialized via {@link registerObservability}).
 */
const LD_SERVER_EXTERNAL_PACKAGES = [
	'@launchdarkly/observability-node',
	'@launchdarkly/node-server-sdk-otel',
	'@prisma/instrumentation',
	'require-in-the-middle',
	'import-in-the-middle',
]

export interface LaunchDarklyConfigOptions {
	/**
	 * Configures same-origin rewrites that proxy browser telemetry through your
	 * own domain to LaunchDarkly. This avoids ad-blockers and keeps requests on
	 * your origin. When enabled, {@link LDObservabilityInit} automatically routes
	 * its requests through the proxy.
	 * @default true
	 */
	configureLaunchDarklyProxy?: boolean
	/**
	 * LaunchDarkly events ingest URL that the `/highlight-events` rewrite
	 * forwards browser telemetry to. Defaults to the production host; override
	 * to point the proxy at LaunchDarkly staging or a local backend (or set the
	 * `LAUNCHDARKLY_BACKEND_URL` env var, or `LAUNCHDARKLY_ENV=staging`).
	 */
	backendUrl?: string
	/**
	 * LaunchDarkly OTLP endpoint that the `/v1/traces`, `/v1/metrics` and
	 * `/v1/logs` rewrites forward browser OTLP data to. Defaults to the
	 * production host; override to point the proxy at LaunchDarkly staging or a
	 * local backend (or set the `LAUNCHDARKLY_OTEL_ENDPOINT` env var, or
	 * `LAUNCHDARKLY_ENV=staging`).
	 */
	otelEndpoint?: string
}

const getDefaultOpts = (
	opts?: LaunchDarklyConfigOptions,
): Required<Pick<LaunchDarklyConfigOptions, 'configureLaunchDarklyProxy'>> &
	Pick<LaunchDarklyConfigOptions, 'backendUrl' | 'otelEndpoint'> => ({
	configureLaunchDarklyProxy: opts?.configureLaunchDarklyProxy ?? true,
	backendUrl: opts?.backendUrl,
	otelEndpoint: opts?.otelEndpoint,
})

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

const getLaunchDarklyConfig = (
	config: NextConfig,
	opts?: LaunchDarklyConfigOptions,
): NextConfig => {
	const defaultOpts = getDefaultOpts(opts)

	let newRewrites = config.rewrites
	if (defaultOpts.configureLaunchDarklyProxy) {
		const { backendUrl, otlpEndpoint } = resolveLDEndpoints({
			backendUrl: defaultOpts.backendUrl,
			otelEndpoint: defaultOpts.otelEndpoint,
		})
		const proxyRewrites: Rewrite[] = [
			{
				source: PROXY_BACKEND_PATH,
				destination: backendUrl,
			},
			{
				source: '/v1/traces',
				destination: `${otlpEndpoint}/v1/traces`,
			},
			{
				source: '/v1/metrics',
				destination: `${otlpEndpoint}/v1/metrics`,
			},
			{
				source: '/v1/logs',
				destination: `${otlpEndpoint}/v1/logs`,
			},
		]

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

			if (!re || Array.isArray(re)) {
				re = re ?? []
				return re.concat(...proxyRewrites)
			}
			return {
				beforeFiles: re.beforeFiles ?? [],
				afterFiles: (re.afterFiles ?? []).concat(...proxyRewrites),
				fallback: re.fallback ?? [],
			}
		}
	}

	const serverExternalPackages = Array.from(
		new Set([
			...(config.serverExternalPackages ?? []),
			...LD_SERVER_EXTERNAL_PACKAGES,
		]),
	)

	return {
		...config,
		env: {
			...config.env,
			[PROXY_ENV_FLAG]: defaultOpts.configureLaunchDarklyProxy
				? 'true'
				: '',
		},
		rewrites: newRewrites,
		serverExternalPackages,
	}
}

export function withLaunchDarklyConfig(
	config: NextConfigObject,
	opts?: LaunchDarklyConfigOptions,
): NextConfig
export function withLaunchDarklyConfig(
	config: NextConfigFunction | NextConfigAsyncFunction,
	opts?: LaunchDarklyConfigOptions,
): NextConfigAsyncFunction
export function withLaunchDarklyConfig(
	config: NextConfigInput,
	opts?: LaunchDarklyConfigOptions,
): NextConfig | NextConfigAsyncFunction {
	if (typeof config === 'function') {
		const phaseHandler: NextConfigAsyncFunction = async (
			phase: string,
			{ defaultConfig }: { defaultConfig: any },
		): Promise<NextConfig> => {
			const userNextConfigObject: NextConfig | Promise<NextConfig> =
				config(phase, { defaultConfig })
			const nc = await userNextConfigObject
			return getLaunchDarklyConfig(nc, opts)
		}
		return phaseHandler
	}
	return getLaunchDarklyConfig(config, opts)
}
