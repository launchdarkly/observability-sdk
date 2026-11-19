import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import {
	ReportingObserverListener,
	type ReportingObserverReport,
} from './reporting-observer-listener'

type ObserverCallback = (reports: ReadonlyArray<unknown>) => void

let deliver: ObserverCallback | undefined

class FakeReportingObserver {
	constructor(cb: ObserverCallback) {
		deliver = cb
	}
	observe() {}
	disconnect() {}
}

// Browsers expose report bodies as ReportBody instances: the fields are
// getters on the prototype, so Object.entries() on the body is empty and
// toJSON() is the way to read them.
class FakeCSPViolationReportBody {
	#fields: Record<string, unknown>
	constructor(fields: Record<string, unknown>) {
		this.#fields = fields
	}
	get effectiveDirective() {
		return this.#fields['effectiveDirective']
	}
	get blockedURL() {
		return this.#fields['blockedURL']
	}
	get disposition() {
		return this.#fields['disposition']
	}
	get originalPolicy() {
		return this.#fields['originalPolicy']
	}
	toJSON() {
		return { ...this.#fields }
	}
}

const cspReport = (fields: Record<string, unknown>) => ({
	type: 'csp-violation',
	url: 'https://app.example.com/projects',
	body: new FakeCSPViolationReportBody(fields),
})

const listen = (options?: Parameters<typeof ReportingObserverListener>[1]) => {
	const reports: ReportingObserverReport[] = []
	const disconnect = ReportingObserverListener(
		(report) => reports.push(report),
		options,
	)
	return { reports, disconnect }
}

describe('ReportingObserverListener', () => {
	beforeEach(() => {
		;(window as any).ReportingObserver = FakeReportingObserver
	})
	afterEach(() => {
		delete (window as any).ReportingObserver
		deliver = undefined
	})

	it('records a CSP violation as a warn log, not an error, by default', () => {
		const { reports } = listen()

		deliver?.([
			cspReport({
				effectiveDirective: 'script-src-elem',
				blockedURL: 'https://cdn.example.net/widget.js',
				disposition: 'report',
			}),
		])

		expect(reports).toHaveLength(1)
		expect(reports[0]).toMatchObject({
			kind: 'log',
			level: 'warn',
			type: 'csp-violation',
		})
	})

	it('records a CSP violation as an error when asked to', () => {
		const { reports } = listen({ cspViolationsAsErrors: true })

		deliver?.([
			cspReport({
				effectiveDirective: 'img-src',
				disposition: 'enforce',
			}),
		])

		expect(reports[0]).toMatchObject({
			kind: 'error',
			type: 'csp-violation',
		})
	})

	it('reads the fields of a ReportBody, whose fields live on its prototype', () => {
		const { reports } = listen()

		deliver?.([
			cspReport({
				effectiveDirective: 'connect-src',
				blockedURL: 'https://collector.example.net',
				disposition: 'report',
			}),
		])

		expect(reports[0].attributes).toMatchObject({
			'report.type': 'csp-violation',
			'report.url': 'https://app.example.com/projects',
			'report.body.effectiveDirective': 'connect-src',
			'report.body.blockedURL': 'https://collector.example.net',
			'report.body.disposition': 'report',
		})
	})

	it('names a CSP violation after its directive', () => {
		const { reports } = listen()

		deliver?.([cspReport({ effectiveDirective: 'style-src-attr' })])

		expect(reports[0].message).toBe('csp-violation: style-src-attr')
	})

	it('falls back to the report type when the directive is unknown', () => {
		const { reports } = listen()

		deliver?.([cspReport({})])

		expect(reports[0].message).toBe('csp-violation')
	})

	it('leaves out the original policy, which repeats the whole CSP on every report', () => {
		const { reports } = listen()

		deliver?.([
			cspReport({
				effectiveDirective: 'script-src',
				originalPolicy: "default-src 'self'; script-src 'self'",
			}),
		])

		expect(reports[0].attributes).not.toHaveProperty(
			'report.body.originalPolicy',
		)
	})

	it('still records intervention reports as errors', () => {
		const { reports } = listen()

		deliver?.([
			{
				type: 'intervention',
				url: 'https://app.example.com/',
				body: {
					id: 'NavigatorVibrate',
					message: 'Blocked call to navigator.vibrate',
				},
			},
		])

		expect(reports[0]).toMatchObject({
			kind: 'error',
			type: 'intervention',
			message: 'Blocked call to navigator.vibrate',
		})
	})

	it('still records deprecation reports as warn logs', () => {
		const { reports } = listen()

		deliver?.([
			{
				type: 'deprecation',
				url: 'https://app.example.com/',
				body: { id: 'UnloadHandler', message: 'unload is deprecated' },
			},
		])

		expect(reports[0]).toMatchObject({
			kind: 'log',
			level: 'warn',
			message: 'unload is deprecated',
		})
	})

	it('is a no-op where the Reporting API is unavailable', () => {
		delete (window as any).ReportingObserver

		const { reports, disconnect } = listen()

		expect(reports).toHaveLength(0)
		expect(() => disconnect()).not.toThrow()
	})
})
