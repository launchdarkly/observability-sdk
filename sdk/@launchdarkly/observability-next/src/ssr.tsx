import Observability, {
	LDObserve,
	type ObserveOptions,
} from '@launchdarkly/observability'
import type { NextPageContext } from 'next'
import NextError, { ErrorProps } from 'next/error.js'
import React, { useEffect } from 'react'

import { ensureStandaloneInit, type LDApplicationInfo } from './util/standalone'

export { ErrorBoundary } from './error-boundary'
export { LDObserve }

export interface LDObservabilityInitProps extends ObserveOptions {
	/** The LaunchDarkly client-side ID (SDK key). */
	sdkKey?: string
	/** Application metadata (id / version) tagged onto telemetry. */
	application?: LDApplicationInfo
}

export type LDErrorProps = { errorMessage: string } & ErrorProps

export function getLDErrorInitialProps({
	res,
	err,
}: NextPageContext): LDErrorProps {
	const statusCode = res?.statusCode ?? err?.statusCode ?? 500
	const errorMessage =
		res?.statusMessage ?? err?.message ?? 'An error occurred'

	return { errorMessage, statusCode }
}

export type PageRouterErrorProps = LDErrorProps

/**
 * Pages Router custom `_error` page handler. Initializes observability in
 * standalone mode (if not already running) and records the rendered error.
 */
export function pageRouterCustomErrorHandler(
	initProps: LDObservabilityInitProps,
	Child?: React.FC<LDErrorProps>,
) {
	const { sdkKey, application, ...options } = initProps

	const handler = (props: LDErrorProps) => {
		if (sdkKey) {
			ensureStandaloneInit(
				'observe',
				() => new Observability(options),
				sdkKey,
				application,
			)
		}
		LDObserve.recordError(new Error(props.errorMessage))

		return Child ? <Child {...props} /> : <NextError {...props} />
	}

	handler.getInitialProps = getLDErrorInitialProps

	return handler
}

export type AppRouterErrorProps = {
	error: Error & { digest?: string }
	reset: () => void
}

/**
 * App Router `error.tsx` wrapper. Records the error to LaunchDarkly (which must
 * already be initialized via {@link LDObservabilityInit}) and renders your error
 * UI.
 */
export function appRouterSsrErrorHandler(Child: React.FC<AppRouterErrorProps>) {
	return ({ error, reset }: AppRouterErrorProps) => {
		useEffect(() => {
			LDObserve.recordError(error)
		}, [error])

		return <Child error={error} reset={reset} />
	}
}
