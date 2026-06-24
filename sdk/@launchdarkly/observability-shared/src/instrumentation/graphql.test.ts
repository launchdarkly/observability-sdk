import { describe, it, expect } from 'vitest'
import { parseGraphQLOperation } from './graphql'

describe('parseGraphQLOperation', () => {
	it('extracts name and type from a named query', () => {
		const body = JSON.stringify({
			query: 'query GetUser($id: ID!) { user(id: $id) { id } }',
			operationName: 'GetUser',
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'GetUser',
			type: 'query',
		})
	})

	it('extracts name and type from a named mutation', () => {
		const body = JSON.stringify({
			query: 'mutation AddItem($name: String!) { addItem(name: $name) { id } }',
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'AddItem',
			type: 'mutation',
		})
	})

	it('extracts name and type from a named subscription', () => {
		const body = JSON.stringify({
			query: 'subscription OnComment { commentAdded { id } }',
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'OnComment',
			type: 'subscription',
		})
	})

	it('prefers the explicit operationName over the document name', () => {
		const body = JSON.stringify({
			query: 'query A { a }\nquery B { b }',
			operationName: 'B',
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'B',
			type: 'query',
		})
	})

	it('parses the name from the document when operationName is absent', () => {
		const body = JSON.stringify({
			query: 'query GetTeams { teams { id } }',
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'GetTeams',
			type: 'query',
		})
	})

	it('treats anonymous shorthand as a query with no name', () => {
		const body = JSON.stringify({ query: '{ viewer { id } }' })
		expect(parseGraphQLOperation(body)).toEqual({
			name: undefined,
			type: 'query',
		})
	})

	it('handles an anonymous keyword operation with no name', () => {
		const body = JSON.stringify({ query: 'mutation { logout }' })
		expect(parseGraphQLOperation(body)).toEqual({
			name: undefined,
			type: 'mutation',
		})
	})

	it('describes the first operation in a batched request', () => {
		const body = JSON.stringify([
			{ query: 'query First { a }', operationName: 'First' },
			{ query: 'query Second { b }', operationName: 'Second' },
		])
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'First',
			type: 'query',
		})
	})

	it('accepts an already-parsed object body', () => {
		expect(
			parseGraphQLOperation({
				query: 'query Ping { ping }',
			}),
		).toEqual({ name: 'Ping', type: 'query' })
	})

	it('supports a persisted query that only sends operationName', () => {
		const body = JSON.stringify({
			operationName: 'GetUser',
			extensions: { persistedQuery: { sha256Hash: 'abc' } },
		})
		expect(parseGraphQLOperation(body)).toEqual({
			name: 'GetUser',
			type: undefined,
		})
	})

	it('returns null for a non-GraphQL JSON body', () => {
		expect(parseGraphQLOperation(JSON.stringify({ foo: 'bar' }))).toBeNull()
	})

	it('returns null for malformed JSON without throwing', () => {
		expect(parseGraphQLOperation('{ not json')).toBeNull()
	})

	it('returns null for empty, null, and undefined bodies', () => {
		expect(parseGraphQLOperation('')).toBeNull()
		expect(parseGraphQLOperation(null)).toBeNull()
		expect(parseGraphQLOperation(undefined)).toBeNull()
	})
})
