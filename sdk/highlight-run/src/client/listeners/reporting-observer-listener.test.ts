import { afterEach, describe, expect, it, vi } from 'vitest'
import {
	ReportingObserverListener,
	ReportingObserverReport,
} from './reporting-observer-listener'

// Mirrors the browser's CSPViolationReportBody: fields are prototype getters plus toJSON().
class FakeCSPViolationReportBody {
	get blockedURL() {
		return 'https://cdn.example.com/app.css'
	}
	get effectiveDirective() {
		return 'style-src-elem'
	}
	toJSON() {
		return {
			blockedURL: this.blockedURL,
			effectiveDirective: this.effectiveDirective,
		}
	}
}

describe('ReportingObserverListener', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('keeps the fields of a report body exposed as getters', () => {
		let deliver: (reports: unknown[]) => void = () => {}
		vi.stubGlobal(
			'ReportingObserver',
			class {
				constructor(cb: (reports: unknown[]) => void) {
					deliver = cb
				}
				observe() {}
				disconnect() {}
			},
		)

		const received: ReportingObserverReport[] = []
		ReportingObserverListener((r) => received.push(r))
		deliver([
			{
				type: 'csp-violation',
				url: 'https://app.example.com/',
				body: new FakeCSPViolationReportBody(),
			},
		])

		expect(received).toHaveLength(1)
		expect(received[0].attributes).toMatchObject({
			'report.body.blockedURL': 'https://cdn.example.com/app.css',
			'report.body.effectiveDirective': 'style-src-elem',
		})
		expect(received[0].message).toBe(
			'CSP style-src-elem blocked https://cdn.example.com/app.css',
		)
	})
})
