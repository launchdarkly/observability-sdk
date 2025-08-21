import { expect, it } from 'vitest'
import { getContextKeys } from './index'
import type { LDContext, LDMultiKindContext } from '@launchdarkly/js-client-sdk'

it.each([
	// Legacy user (no kind property)
	[{ key: 'bob' }, { user: 'bob' }],
	// Single kind context - user
	[{ kind: 'user', key: 'bob' }, { user: 'bob' }],
	// Single kind context - organization
	[{ kind: 'org', key: 'org123' }, { org: 'org123' }],
	// Single kind context - device
	[{ kind: 'device', key: 'device456' }, { device: 'device456' }],
	// Multi-kind context
	[
		{
			kind: 'multi',
			user: {
				key: 'user-key',
				name: 'Test User',
			},
			org: {
				key: 'org-key',
				name: 'Test Org',
			},
		} as LDMultiKindContext,
		{
			org: 'org-key',
			user: 'user-key',
		},
	],
	// Multi-kind context with different order (should still be sorted)
	[
		{
			kind: 'multi',
			device: {
				key: 'device-key',
				name: 'Test Device',
			},
			user: {
				key: 'user-key',
				name: 'Test User',
			},
		} as LDMultiKindContext,
		{
			device: 'device-key',
			user: 'user-key',
		},
	],
])(
	'should produce the correct context keys for a given context',
	(context: LDContext, expectedKeys: Record<string, string>) => {
		const result = getContextKeys(context)
		expect(result).toEqual(expectedKeys)
	},
)
