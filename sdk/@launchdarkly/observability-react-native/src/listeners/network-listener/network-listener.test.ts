import { describe, it, expect, vi } from 'vitest'
import { Span } from '@opentelemetry/api'
import { FetchHook } from './network-listener'
import { NetworkRecordingOptions } from '../../api/Options'

const createMockSpan = () => {
	const attributes: Record<string, unknown> = {}
	let name = 'HTTP POST'
	const span = {
		setAttribute: vi.fn((key: string, value: unknown) => {
			attributes[key] = value
			return span
		}),
		updateName: vi.fn((newName: string) => {
			name = newName
			return span
		}),
	}
	return {
		span: span as unknown as Span,
		attributes,
		updateName: span.updateName,
		getName: () => name,
	}
}

// Body recording disabled: GraphQL naming must still happen because it reads
// only the low-sensitivity operation name/type, never stores the body.
const recording: NetworkRecordingOptions = { recordHeadersAndBody: false }

describe('FetchHook GraphQL span naming', () => {
	it('renames a GraphQL fetch and sets semconv attributes', () => {
		const { span, attributes, getName } = createMockSpan()
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
		expect(getName()).toBe('query GetUser')
	})

	it('infers the type for an anonymous query with no operation name', () => {
		const { span, attributes, getName } = createMockSpan()
		const body = JSON.stringify({ query: '{ viewer { id } }' })

		FetchHook(recording, [])(
			span,
			{ body } as RequestInit,
			undefined as any,
		)

		expect(attributes['graphql.operation.type']).toBe('query')
		expect(attributes['graphql.operation.name']).toBeUndefined()
		expect(getName()).toBe('query')
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
