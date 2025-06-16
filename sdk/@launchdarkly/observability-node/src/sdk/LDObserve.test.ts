import {
	HIGHLIGHT_REQUEST_HEADER,
	ObservabilityClient,
} from '../client/ObservabilityClient.js'
import { LDObserve, _LDObserve } from './LDObserve.js'
import { describe, expect, it } from 'vitest'

describe('parseHeaders', () => {
	_LDObserve._init(new ObservabilityClient('1'))

	it('returns session id and request id from the headers', () => {
		expect(
			LDObserve.parseHeaders({ [HIGHLIGHT_REQUEST_HEADER]: '1234/5678' }),
		).toMatchObject({ secureSessionId: '1234', requestId: '5678' })
	})

	it('returns undefined if headers is empty', async () => {
		expect(LDObserve.parseHeaders({})).toMatchObject({
			secureSessionId: undefined,
			requestId: undefined,
		})
	})

	it('returns session if request is invalid', async () => {
		expect(
			LDObserve.parseHeaders({
				[HIGHLIGHT_REQUEST_HEADER]: 'not valid!',
			}),
		).toMatchObject({ secureSessionId: 'not valid!', requestId: undefined })
	})
})
