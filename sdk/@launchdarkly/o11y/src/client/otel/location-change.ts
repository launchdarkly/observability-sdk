import {
	InstrumentationBase,
	InstrumentationConfig,
} from '@opentelemetry/instrumentation'
import * as SemanticAttributes from '@opentelemetry/semantic-conventions'
import { LD_PAGE_VIEW_SPAN_NAME } from '../../integrations/launchdarkly'
import { PathListener } from '../listeners/path-listener'

export type LocationChangeConfig = InstrumentationConfig

/**
 * Instrumentation that fires a span whenever the URL changes.
 * Delegates URL change detection to PathListener.
 */
export class LocationChangeInstrumentation extends InstrumentationBase {
	static readonly version = '0.1.0'
	static readonly moduleName = 'location-change'

	private _unlisten: (() => void) | undefined
	private _lastUrl: string = ''

	constructor(config: LocationChangeConfig = {}) {
		super(
			LocationChangeInstrumentation.moduleName,
			LocationChangeInstrumentation.version,
			config,
		)
	}

	init() {}

	override enable() {
		this._lastUrl = window.location.href

		this._unlisten = PathListener((currentUrl) => {
			const previousUrl = this._lastUrl
			this._lastUrl = currentUrl
			if (previousUrl === currentUrl) {
				return
			}
			try {
				const span = this.tracer.startSpan(LD_PAGE_VIEW_SPAN_NAME, {
					attributes: {
						[SemanticAttributes.ATTR_URL_FULL]: currentUrl,
						'page_view.previous_url': previousUrl,
						'page_view.url': currentUrl,
					},
				})
				span.end()
			} catch (e) {
				this._diag.error('failed to create page view span', e)
			}
		})
	}

	override disable() {
		this._unlisten?.()
		this._unlisten = undefined
	}
}
