import { Rewrite } from 'next/dist/lib/load-custom-routes'
import { withHighlightConfig } from './with-highlight-config'
import { describe, expect, it } from 'vitest'

describe('withHighlightConfig', () => {
	let defaultRewrite = [
		{
			destination: 'https://pub.highlight.io',
			source: '/highlight-events',
		},
		{
			destination: 'https://otel.highlight.io/v1/traces',
			source: '/v1/traces',
		},
		{
			destination: 'https://otel.highlight.io/v1/metrics',
			source: '/v1/metrics',
		},
		{
			destination: 'https://otel.highlight.io/v1/logs',
			source: '/v1/logs',
		},
	]

	it('creates new rewrites if none exist', async () => {
		expect(
			await (
				await withHighlightConfig({}, { uploadSourceMaps: true })
			).rewrites!(),
		).toMatchObject(defaultRewrite)
	})

	it('adds to a wrapped rewrites array', async () => {
		const testEntry = {
			source: '/test-rewrite',
			destination: 'http://www.example.com',
		}
		const rewrites: () => Promise<Rewrite[]> = () =>
			Promise.resolve([testEntry])

		const expected = [testEntry, ...defaultRewrite]
		expect(
			await (
				await withHighlightConfig({ rewrites })
			).rewrites!(),
		).toMatchObject(expected)
	})

	it('adds to a wrapped rewrites object', async () => {
		const testEntry = {
			source: '/test-rewrite',
			destination: 'http://www.example.com',
		}
		const rewrites: () => Promise<{
			beforeFiles: Rewrite[]
			afterFiles: Rewrite[]
			fallback: Rewrite[]
		}> = () =>
			Promise.resolve({
				beforeFiles: [testEntry],
				afterFiles: [],
				fallback: [testEntry],
			})

		const expected = {
			beforeFiles: [testEntry],
			fallback: [testEntry],
		}
		expect(
			await (
				await withHighlightConfig({ rewrites })
			).rewrites!(),
		).toMatchObject(expected)
	})

	it('assumes rewrites may not have all fields defined', async () => {
		const testEntry = {
			source: '/test-rewrite',
			destination: 'http://www.example.com',
		}
		const rewrites: () => Promise<{
			beforeFiles: Rewrite[]
			afterFiles: Rewrite[]
			fallback: Rewrite[]
		}> =
			// @ts-expect-error
			() => Promise.resolve({ fallback: [testEntry] })

		const expected = {
			beforeFiles: [],
			afterFiles: defaultRewrite,
			fallback: [testEntry],
		}
		expect(
			await (
				await withHighlightConfig({ rewrites })
			).rewrites!(),
		).toMatchObject(expected)
	})

	it('assumes rewrites may return undefined', async () => {
		const testEntry = {
			source: '/test-rewrite',
			destination: 'http://www.example.com',
		}
		const rewrites: () => Promise<{
			beforeFiles: Rewrite[]
			afterFiles: Rewrite[]
			fallback: Rewrite[]
		}> =
			// @ts-expect-error
			() => Promise.resolve(undefined)

		expect(
			await (
				await withHighlightConfig({ rewrites })
			).rewrites!(),
		).toMatchObject(defaultRewrite)
	})

	it('adds OpenTelemetry packages to serverExternalPackages', async () => {
		const result = await withHighlightConfig({})
		expect(result.serverExternalPackages).toEqual(
			expect.arrayContaining([
				'@highlight-run/node',
				'@opentelemetry/instrumentation',
				'@prisma/instrumentation',
				'require-in-the-middle',
				'import-in-the-middle',
			]),
		)
	})

	it('preserves user-provided serverExternalPackages and deduplicates', async () => {
		const result = await withHighlightConfig({
			serverExternalPackages: ['pino', '@opentelemetry/instrumentation'],
		})
		expect(result.serverExternalPackages).toEqual(
			expect.arrayContaining([
				'pino',
				'@opentelemetry/instrumentation',
				'@highlight-run/node',
			]),
		)
		expect(
			(result.serverExternalPackages ?? []).filter(
				(p) => p === '@opentelemetry/instrumentation',
			),
		).toHaveLength(1)
	})

	it('adds ignoreWarnings for OpenTelemetry on the server build', async () => {
		const result = await withHighlightConfig({})
		const webpack = result.webpack as (cfg: any, opts: any) => any
		const cfg = webpack({ plugins: [] }, { isServer: true } as any)
		expect(cfg.ignoreWarnings).toEqual(
			expect.arrayContaining([
				expect.objectContaining({
					message: expect.any(RegExp),
					module: expect.any(RegExp),
				}),
			]),
		)
	})

	it('does not add ignoreWarnings on the client build', async () => {
		const result = await withHighlightConfig({})
		const webpack = result.webpack as (cfg: any, opts: any) => any
		const cfg = webpack({ plugins: [] }, { isServer: false } as any)
		expect(cfg.ignoreWarnings).toBeUndefined()
	})
})
