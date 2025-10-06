import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../client/workers/highlight-client-worker?worker&inline', () => {
	class FakeWorker {
		onmessage = (_e: any) => {}
		postMessage = vi.fn((_message: any) => null)
	}
	return { default: FakeWorker }
})

import { RecordSDK } from '../sdk/record'
import { LDRecord } from '../sdk/LDRecord'
import { MessageType } from '../client/workers/types'

describe('Record addSessionProperties', () => {
	let recordImpl: RecordSDK

	beforeEach(() => {
		recordImpl = new RecordSDK({
			organizationID: '1',
			environment: 'test',
			sessionSecureID: 'test-session',
		})
	})

	it('posts Properties message with session type to the worker', () => {
		const postSpy = vi.spyOn((recordImpl as any)._worker, 'postMessage')

		const props = { plan: 'pro', count: 1, active: true }
		recordImpl.addSessionProperties(props)

		expect(postSpy).toHaveBeenCalled()
		const call = postSpy.mock.calls[0]?.[0] as any
		expect(call?.message?.type).toBe(MessageType.Properties)
		expect(call?.message?.propertyType).toEqual({ type: 'session' })
		expect(call?.message?.propertiesObject).toMatchObject(props)
	})

	it('LDRecord proxies addSessionProperties to implementation', () => {
		// Load the implementation into the LDRecord buffered proxy
		LDRecord.load(recordImpl)

		const spy = vi.spyOn(recordImpl, 'addSessionProperties')
		const props = { foo: 'bar' }
		LDRecord.addSessionProperties(props)

		expect(spy).toHaveBeenCalledWith(props)
	})
})
