import { describe, it, expect, vi } from 'vitest'
import { Span } from '@opentelemetry/api'
import { FetchHook } from './network-listener'
import { NetworkRecordingOptions } from '../../api/Options'

const createMockSpan = () => {
	const attributes: Record<string, unknown> = {}
	const updateName = vi.fn()
	const span = {
		setAttribute: vi.fn((key: string, value: unknown) => {
			attributes[key] = value
			return span
		}),
		updateName,
	}
	return { span: span as unknown as Span, attributes, updateName }
}

// Body recording off: tagging must still run.
const recording: NetworkRecordingOptions = { recordHeadersAndBody: false }

describe('FetchHook GraphQL operation attributes', () => {
	it('sets semconv attributes for a GraphQL fetch without renaming the span', () => {
		const { span, attributes, updateName } = createMockSpan()
		const body = JSON.stringify({
			query: 'query GetUser($id: ID!) { user(id: $id) { id } }',
			operationName: 'GetUser',
		})

		FetchHook(recording, [])(
			span,
			{ body } as RequestInit,
			undefined as any,
		)

		expect(attributes['graphql.operation.name']).toBe('GetUser')
		expect(attributes['graphql.operation.type']).toBe('query')
		expect(updateName).not.toHaveBeenCalled()
	})

	it('infers the type for an anonymous query with no operation name', () => {
		const { span, attributes, updateName } = createMockSpan()
		const body = JSON.stringify({ query: '{ viewer { id } }' })

		FetchHook(recording, [])(
			span,
			{ body } as RequestInit,
			undefined as any,
		)

		expect(attributes['graphql.operation.type']).toBe('query')
		expect(attributes['graphql.operation.name']).toBeUndefined()
		expect(updateName).not.toHaveBeenCalled()
	})

	it('leaves a non-GraphQL fetch untouched', () => {
		const { span, attributes, updateName } = createMockSpan()
		const body = JSON.stringify({ hello: 'world' })

		FetchHook(recording, [])(
			span,
			{ body } as RequestInit,
			undefined as any,
		)

		expect(attributes['graphql.operation.name']).toBeUndefined()
		expect(updateName).not.toHaveBeenCalled()
	})
})
