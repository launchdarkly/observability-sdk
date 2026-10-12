import Observability, {
	LDObserve,
	type ObserveOptions,
} from '@launchdarkly/observability'
import SessionReplay, {
	LDRecord,
	type RecordOptions,
} from '@launchdarkly/session-replay'
import Cookies from 'js-cookie'
import { useEffect } from 'react'

import { PROXY_BACKEND_PATH, PROXY_ENV_FLAG } from './util/proxy'
import { ensureStandaloneInit, type LDApplicationInfo } from './util/standalone'

export { ErrorBoundary } from './error-boundary'
export type { ErrorBoundaryProps, ErrorBoundaryState } from './error-boundary'
export { LDObserve, LDRecord }

/**
 * Options for {@link LDObservabilityInit}. Combines the browser observability and
 * session replay options, plus the LaunchDarkly SDK key used to initialize them
 * in standalone mode.
 */
export type LDObservabilityInitProps = ObserveOptions &
	RecordOptions & {
		/**
		 * The LaunchDarkly client-side ID (SDK key) for the environment to send
		 * telemetry to. Required for the SDK to initialize.
		 */
		sdkKey?: string
		/**
		 * Application metadata (id / version) tagged onto telemetry, mirroring
		 * the `application` field of a LaunchDarkly client configuration.
		 */
		application?: LDApplicationInfo
		/**
		 * Hostnames on which the SDK should not initialize (e.g. local
		 * development domains). If the current hostname includes any of these
		 * substrings the SDK is skipped.
		 */
		excludedHostnames?: string[]
		/**
		 * Whether the same-origin telemetry proxy rewrites (from
		 * `withLaunchDarklyConfig`) are configured. When true, telemetry is
		 * uploaded to `/highlight-events` plus same-origin OTLP paths, which the
		 * Next server rewrites forward to LaunchDarkly.
		 *
		 * Compute it server-side with `process.env[PROXY_ENV_FLAG] === 'true'` —
		 * the server runtime has the `env` map from `next.config`, while client
		 * bundles cannot (Turbopack does not inline `next.config.js` `env` map
		 * values into the client bundle). If omitted, the SDK falls back to the
		 * (less reliable) client-side env probe.
		 */
		useProxy?: boolean
	}

/**
 * Drop-in client component that boots LaunchDarkly observability and session
 * replay for a Next.js app. Render it once near the root of your app (e.g. in
 * `app/layout.tsx` or `pages/_app.tsx`).
 *
 * It runs the plugins in standalone mode — there is no LaunchDarkly feature-flag
 * SDK for Next.js, so the plugins are initialized directly from the LaunchDarkly
 * SDK key rather than registered with an LD client.
 */
export function LDObservabilityInit({
	sdkKey,
	application,
	excludedHostnames = [],
	useProxy,
	...options
}: LDObservabilityInitProps) {
	useEffect(() => {
		const shouldRender =
			!!sdkKey &&
			excludedHostnames.every(
				(hostname) => !window.location.hostname.includes(hostname),
			)

		if (!shouldRender) {
			return
		}

		let initOptions: LDObservabilityInitProps = { ...options }

		// `useProxy` is normally passed as a prop by a server layout (computed
		// from the withLaunchDarklyConfig env flag). Fall back to a client-side
		// probe of the same flag for callers that don't pass it — note this probe
		// only sees a value when the bundler inlined next.config's env map (e.g.
		// webpack builds).
		const configureProxy =
			useProxy ?? process.env[PROXY_ENV_FLAG] === 'true'
		if (configureProxy) {
			initOptions = {
				...initOptions,
				backendUrl: PROXY_BACKEND_PATH,
				otel: {
					...initOptions.otel,
					otlpEndpoint: window.location.origin,
				},
			}
		}

		// Standalone init loads the LDObserve / LDRecord singletons. The shared
		// guard keeps this idempotent across re-renders and bundles.
		ensureStandaloneInit(
			'observe',
			() => new Observability(initOptions),
			sdkKey,
			application,
		)
		ensureStandaloneInit(
			'record',
			() => new SessionReplay(initOptions),
			sdkKey,
			application,
		)

		// The session secure id only becomes available once the session replay
		// SDK has finished loading, so LDRecord.getSession() returns null right
		// after init. Poll briefly until it resolves, then persist it for
		// observabilityMiddleware to forward as x-highlight-request. If replay
		// never loads, there is no session to link and the cookie is left unset.
		const persistSessionCookie = () => {
			const sessionSecureID = LDRecord.getSession()?.sessionSecureID
			if (sessionSecureID) {
				Cookies.set('sessionSecureID', sessionSecureID)
				return true
			}
			return false
		}
		if (persistSessionCookie()) {
			return
		}
		let attempts = 0
		const interval = setInterval(() => {
			if (persistSessionCookie() || ++attempts >= 50) {
				clearInterval(interval)
			}
		}, 100)
		return () => clearInterval(interval)
	}, []) // eslint-disable-line react-hooks/exhaustive-deps

	return null
}
