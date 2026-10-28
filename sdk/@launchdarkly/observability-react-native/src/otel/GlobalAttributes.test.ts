import { beforeEach, describe, expect, it } from 'vitest'
import { GlobalAttributes } from './GlobalAttributes'

describe('GlobalAttributes', () => {
	let store: GlobalAttributes

	beforeEach(() => {
		store = new GlobalAttributes()
	})

	it('merges attributes by key', () => {
		store.setAttributes({ a: 1, b: 'two' })
		store.setAttributes({ b: 'three', c: true })

		expect(store.getAttributes()).toEqual({ a: 1, b: 'three', c: true })
	})

	it('removes attributes by key', () => {
		store.setAttributes({ a: 1, b: 2, c: 3 })
		store.removeAttributes(['a', 'c', 'missing'])

		expect(store.getAttributes()).toEqual({ b: 2 })
	})

	it('ignores reserved session keys and reports them', () => {
		const ignored = store.setAttributes({
			'highlight.session_id': 'spoofed',
			'session.id': 'spoofed',
			kept: 'yes',
		})

		expect(ignored).toEqual(['highlight.session_id', 'session.id'])
		expect(store.getAttributes()).toEqual({ kept: 'yes' })
	})

	it('ignores values that are not valid attribute values', () => {
		const ignored = store.setAttributes({
			missing: undefined,
			nested: { a: 1 } as any,
			mixed: [1, 'two'] as any,
			list: ['a', 'b'],
		})

		expect(ignored).toEqual(['missing', 'nested', 'mixed'])
		expect(store.getAttributes()).toEqual({ list: ['a', 'b'] })
	})

	it('exposes an immutable snapshot that is replaced on change', () => {
		const list = ['a']
		store.setAttributes({ list })
		const snapshot = store.getAttributes()

		list.push('b')
		expect(Object.isFrozen(snapshot)).toBe(true)
		expect(snapshot.list).toEqual(['a'])
		expect(store.getAttributes()).toBe(snapshot)

		store.setAttributes({ other: 1 })
		expect(store.getAttributes()).not.toBe(snapshot)
		expect(snapshot).toEqual({ list: ['a'] })
	})

	it('does not rebuild the snapshot when nothing changes', () => {
		store.setAttributes({ a: 1 })
		const snapshot = store.getAttributes()

		store.setAttributes({ 'session.id': 'x' })
		store.removeAttributes(['missing'])

		expect(store.getAttributes()).toBe(snapshot)
	})
})
