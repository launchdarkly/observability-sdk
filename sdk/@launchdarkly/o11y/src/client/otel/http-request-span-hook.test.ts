import { describe, expect, it, vi } from 'vitest'
import type { Span } from '@opentelemetry/api'
import { applyHttpRequestSpanHook } from './index'

const makeSpan = (attributes: Record<string, unknown>) =>
	({
		attributes,
		name: 'HTTP GET',
		updateName: vi.fn(function (this: any, name: string) {
			this.name = name
			return this
		}),
		setAttributes: vi.fn(function (this: any, attrs: Record<string, any>) {
			Object.assign(this.attributes, attrs)
			return this
		}),
	}) as unknown as Span & {
		attributes: Record<string, unknown>
		name: string
	}

describe('applyHttpRequestSpanHook', () => {
	it('does nothing without a hook', () => {
		expect(() =>
			applyHttpRequestSpanHook(undefined, 'fetch', makeSpan({})),
		).not.toThrow()
	})

	it('passes method and url read from the span attributes', () => {
		const hook = vi.fn()
		const span = makeSpan({
			'http.method': 'get',
			'http.url': 'https://app.example.com/api/v2/projects/default',
		})
		applyHttpRequestSpanHook(hook, 'fetch', span)
		expect(hook).toHaveBeenCalledWith(span, {
			type: 'fetch',
			method: 'GET',
			url: 'https://app.example.com/api/v2/projects/default',
		})
	})

	it('falls back to the supplied request details for xhr', () => {
		const hook = vi.fn()
		const span = makeSpan({})
		applyHttpRequestSpanHook(hook, 'xhr', span, {
			method: 'post',
			url: '/api/v2/flags',
		})
		expect(hook).toHaveBeenCalledWith(span, {
			type: 'xhr',
			method: 'POST',
			url: '/api/v2/flags',
		})
	})

	it('lets the hook rename the span and set route attributes', () => {
		const span = makeSpan({
			'http.method': 'GET',
			'http.url': 'https://app.example.com/api/v2/projects/default',
		})
		applyHttpRequestSpanHook(
			(s, { method }) => {
				const route = '/api/v2/projects/{projectKey}'
				s.updateName(`${method} ${route}`)
				s.setAttributes({
					'http.route': route,
					'url.template': route,
					'launchdarkly.operation.name': route,
				})
			},
			'fetch',
			span,
		)
		expect(span.name).toBe('GET /api/v2/projects/{projectKey}')
		expect(span.attributes).toMatchObject({
			'http.route': '/api/v2/projects/{projectKey}',
			'url.template': '/api/v2/projects/{projectKey}',
			'launchdarkly.operation.name': '/api/v2/projects/{projectKey}',
		})
	})

	it('swallows errors thrown by the hook', () => {
		const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
		expect(() =>
			applyHttpRequestSpanHook(
				() => {
					throw new Error('boom')
				},
				'fetch',
				makeSpan({}),
			),
		).not.toThrow()
		expect(warn).toHaveBeenCalled()
		warn.mockRestore()
	})
})
