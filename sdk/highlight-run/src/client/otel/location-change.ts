import {
	InstrumentationBase,
	InstrumentationConfig,
	isWrapped,
} from '@opentelemetry/instrumentation'
import { Attributes } from '@opentelemetry/api'
import * as SemanticAttributes from '@opentelemetry/semantic-conventions'
import { LD_PAGE_VIEW_SPAN_NAME } from 'integrations/launchdarkly'

export type LocationChangeConfig = InstrumentationConfig & {
	getLDContextKeyAttributes?: () => Attributes | undefined
}

/**
 * Instrumentation that fires a span whenever the URL changes.
 * Patches the History API (pushState, replaceState, back, forward, go)
 * and listens to the popstate event.
 */
export class LocationChangeInstrumentation extends InstrumentationBase {
	static readonly version = '0.1.0'
	static readonly moduleName = 'location-change'

	private _getLDContextKeyAttributes:
		| (() => Attributes | undefined)
		| undefined
	private _popstateHandler: (() => void) | undefined
	private _pollInterval: ReturnType<typeof setInterval> | undefined
	private _lastUrl: string = ''

	constructor(config: LocationChangeConfig = {}) {
		super(
			LocationChangeInstrumentation.moduleName,
			LocationChangeInstrumentation.version,
			config,
		)
		this._getLDContextKeyAttributes = config.getLDContextKeyAttributes
	}

	init() {}

	private _createPageViewSpan(previousUrl: string, currentUrl: string) {
		try {
			const span = this.tracer.startSpan(LD_PAGE_VIEW_SPAN_NAME, {
				attributes: {
					[SemanticAttributes.ATTR_URL_FULL]: currentUrl,
					'page_view.previous_url': previousUrl,
					'page_view.url': currentUrl,
				},
			})

			const contextKeys = this._getLDContextKeyAttributes?.()
			if (contextKeys) {
				span.setAttributes(contextKeys)
			}

			span.end()
		} catch (e) {
			this._diag.error('failed to create page view span', e)
		}
	}

	private _patchHistoryMethod() {
		const plugin = this
		return (original: (...args: any[]) => any) => {
			return function patchedHistoryMethod(
				this: History,
				...args: unknown[]
			) {
				const result = original.apply(this, args)
				plugin._checkLocation()
				return result
			}
		}
	}

	private _checkLocation() {
		const currentUrl = window.location.href
		if (this._lastUrl !== currentUrl) {
			this._createPageViewSpan(this._lastUrl, currentUrl)
			this._lastUrl = currentUrl
		}
	}

	override enable() {
		this._lastUrl = window.location.href

		this._wrap(history, 'pushState', this._patchHistoryMethod())
		this._wrap(history, 'replaceState', this._patchHistoryMethod())
		this._wrap(history, 'back', this._patchHistoryMethod())
		this._wrap(history, 'forward', this._patchHistoryMethod())
		this._wrap(history, 'go', this._patchHistoryMethod())

		const plugin = this
		this._popstateHandler = () => plugin._checkLocation()
		window.addEventListener('popstate', this._popstateHandler)

		this._pollInterval = setInterval(() => plugin._checkLocation(), 300)
	}

	override disable() {
		if (isWrapped(history.pushState)) this._unwrap(history, 'pushState')
		if (isWrapped(history.replaceState))
			this._unwrap(history, 'replaceState')
		if (isWrapped(history.back)) this._unwrap(history, 'back')
		if (isWrapped(history.forward)) this._unwrap(history, 'forward')
		if (isWrapped(history.go)) this._unwrap(history, 'go')

		if (this._popstateHandler) {
			window.removeEventListener('popstate', this._popstateHandler)
			this._popstateHandler = undefined
		}

		if (this._pollInterval !== undefined) {
			clearInterval(this._pollInterval)
			this._pollInterval = undefined
		}
	}
}
