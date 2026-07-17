import { createRequire } from 'module'
import { readFileSync, mkdtempSync } from 'fs'
import { tmpdir } from 'os'
import { join } from 'path'
import { describe, it, expect } from 'vitest'

// The Metro plugin is a root-level CommonJS module (Metro configs are CJS), so
// load it via createRequire rather than an ESM import.
const require = createRequire(import.meta.url)
const {
	withLaunchDarklySymbolsId,
	computeHtlhash,
	SYMBOLS_ID_PLACEHOLDER,
	SYMBOLS_ID_GLOBAL,
} = require('../../metro.js')

describe('computeHtlhash', () => {
	it('returns a 32-char hex id', () => {
		const id = computeHtlhash(Buffer.from('hello world'))
		expect(id).toMatch(/^[0-9a-f]{32}$/)
	})

	it('is deterministic and content-sensitive', () => {
		const a = computeHtlhash(Buffer.from('same bytes'))
		const b = computeHtlhash(Buffer.from('same bytes'))
		const c = computeHtlhash(Buffer.from('other bytes'))
		expect(a).toBe(b)
		expect(a).not.toBe(c)
	})
})

describe('withLaunchDarklySymbolsId', () => {
	const fakeGraph = { dependencies: new Map() } as any

	it('overwrites the placeholder with htlhash(map), preserving length', async () => {
		const map = JSON.stringify({ version: 3, mappings: 'AAAA' })
		const previous = () => ({
			code: `globalThis.${SYMBOLS_ID_GLOBAL}=${JSON.stringify(
				SYMBOLS_ID_PLACEHOLDER,
			)};console.log(1);`,
			map,
		})
		const config = { serializer: { customSerializer: previous } }

		const wrapped = withLaunchDarklySymbolsId(config)
		const out = await wrapped.serializer.customSerializer(
			'entry.js',
			[],
			fakeGraph,
			{},
		)

		const expectedId = computeHtlhash(Buffer.from(map))
		expect(out.code).toContain(expectedId)
		expect(out.code).not.toContain(SYMBOLS_ID_PLACEHOLDER)
		// Same-length replacement keeps the bundle byte length stable.
		expect(out.code.length).toBe(previous().code.length)
	})

	it('writes the id to LD_SYMBOLS_ID_SIDECAR when set', async () => {
		const dir = mkdtempSync(join(tmpdir(), 'ld-symbolsid-'))
		const sidecar = join(dir, 'main.jsbundle.map.symbolsid')
		process.env.LD_SYMBOLS_ID_SIDECAR = sidecar
		try {
			const map = JSON.stringify({ version: 3, mappings: 'AACA' })
			const previous = () => ({
				code: `globalThis.${SYMBOLS_ID_GLOBAL}=${JSON.stringify(
					SYMBOLS_ID_PLACEHOLDER,
				)};`,
				map,
			})
			const wrapped = withLaunchDarklySymbolsId({
				serializer: { customSerializer: previous },
			})
			await wrapped.serializer.customSerializer('entry.js', [], fakeGraph, {})

			expect(readFileSync(sidecar, 'utf8')).toBe(
				computeHtlhash(Buffer.from(map)),
			)
		} finally {
			delete process.env.LD_SYMBOLS_ID_SIDECAR
		}
	})

	it('passes through a string result (no map) untouched', async () => {
		const previous = () => 'plain-bundle-code'
		const wrapped = withLaunchDarklySymbolsId({
			serializer: { customSerializer: previous },
		})
		const out = await wrapped.serializer.customSerializer(
			'entry.js',
			[],
			fakeGraph,
			{},
		)
		expect(out).toBe('plain-bundle-code')
	})
})
