import { getSpanName } from './utils'

describe('getSpanName', () => {
	describe('REST API naming', () => {
		it('should create meaningful names for generic API endpoints', () => {
			expect(
				getSpanName('https://api.example.com/users', 'GET', null),
			).toBe('GET /users')
		})

		it('should normalize resource IDs with parameter placeholders', () => {
			expect(
				getSpanName('https://api.example.com/users/123', 'GET', null),
			).toBe('GET /users/:usersId')
		})

		it('should handle UUID resource IDs', () => {
			expect(
				getSpanName(
					'https://api.example.com/orders/a1b2c3d4-e5f6-7890-abcd-ef1234567890',
					'POST',
					null,
				),
			).toBe('POST /orders/:ordersId')
		})

		it('should handle the real-world Zendesk example from the issue', () => {
			expect(
				getSpanName(
					'https://ekr.zdassets.com/compose/d2796142-cc80-467a-95f0-4f4eb1e9acc1',
					'GET',
					null,
				),
			).toBe('GET /compose/:composeId')
		})

		it('should handle nested resources', () => {
			expect(
				getSpanName(
					'https://api.example.com/users/123/orders',
					'GET',
					null,
				),
			).toBe('GET /users/:usersId/orders')
		})

		it('should handle nested resources with parameter placeholders', () => {
			expect(
				getSpanName(
					'https://api.example.com/users/123/orders/456',
					'GET',
					null,
				),
			).toBe('GET /users/:usersId/orders/:ordersId')
		})

		it('should not include query parameters', () => {
			expect(
				getSpanName(
					'https://api.example.com/search?q=test&limit=10',
					'GET',
					null,
				),
			).toBe('GET /search')
		})

		it('should handle single query parameter', () => {
			expect(
				getSpanName(
					'https://api.example.com/search?q=test',
					'GET',
					null,
				),
			).toBe('GET /search')
		})

		it('should handle root path', () => {
			expect(getSpanName('https://api.example.com/', 'GET', null)).toBe(
				'GET /',
			)
		})

		it('should handle root path without trailing slash', () => {
			expect(getSpanName('https://api.example.com', 'GET', null)).toBe(
				'GET /',
			)
		})
	})

	describe('GraphQL naming', () => {
		it('should extract named query operations', () => {
			const body = JSON.stringify({
				operationName: 'GetUser',
				query: 'query GetUser($id: ID!) { user(id: $id) { name } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (GetUser)')
		})

		it('should use operationName from body when available', () => {
			const body = JSON.stringify({
				operationName: 'FetchUserProfile',
				query: 'query { user { name email } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (FetchUserProfile)')
		})

		it('should extract operation name from query string when named', () => {
			const body = JSON.stringify({
				query: 'query FetchUserProfile { user { name email } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (FetchUserProfile)')
		})

		it('should handle anonymous queries', () => {
			const body = JSON.stringify({
				query: 'query { users { name } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (unknown)')
		})

		it('should handle mutations', () => {
			const body = JSON.stringify({
				query: 'mutation CreateUser { createUser(input: {name: "John"}) { id } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (CreateUser)')
		})

		it('should handle anonymous mutations', () => {
			const body = JSON.stringify({
				query: 'mutation { createUser(input: {name: "John"}) { id } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (unknown)')
		})

		it('should handle subscriptions', () => {
			const body = JSON.stringify({
				query: 'subscription UserUpdates { userUpdated { id name } }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (UserUpdates)')
		})

		it('should handle simple anonymous queries', () => {
			const body = JSON.stringify({
				query: '{ users }',
			})
			expect(
				getSpanName('https://api.example.com/graphql', 'POST', body),
			).toBe('POST /graphql (unknown)')
		})
	})

	describe('edge cases', () => {
		it('should handle missing body', () => {
			expect(
				getSpanName('https://api.example.com/users', 'GET', null),
			).toBe('GET /users')
		})

		it('should handle undefined body', () => {
			expect(
				getSpanName('https://api.example.com/users', 'GET', undefined),
			).toBe('GET /users')
		})

		it('should handle invalid JSON body gracefully', () => {
			expect(
				getSpanName(
					'https://api.example.com/users',
					'POST',
					'invalid json',
				),
			).toBe('POST /users')
		})

		it('should handle empty body', () => {
			expect(
				getSpanName('https://api.example.com/users', 'POST', ''),
			).toBe('POST /users')
		})

		it('should handle different HTTP methods', () => {
			expect(
				getSpanName('https://api.example.com/users', 'put', null),
			).toBe('PUT /users')
			expect(
				getSpanName('https://api.example.com/users', 'delete', null),
			).toBe('DELETE /users')
			expect(
				getSpanName('https://api.example.com/users', 'patch', null),
			).toBe('PATCH /users')
		})

		it('should handle complex paths with multiple segments', () => {
			expect(
				getSpanName(
					'https://api.example.com/v1/users/profile/settings',
					'GET',
					null,
				),
			).toBe('GET /v1/users/profile/settings')
		})

		it('should handle short IDs', () => {
			expect(
				getSpanName('https://api.example.com/users/1', 'GET', null),
			).toBe('GET /users/:usersId')
		})

		it('should handle long alphanumeric IDs', () => {
			expect(
				getSpanName(
					'https://api.example.com/users/abc123def456',
					'GET',
					null,
				),
			).toBe('GET /users/:usersId')
		})

		it('should not treat regular words as IDs', () => {
			expect(
				getSpanName(
					'https://api.example.com/users/profile',
					'GET',
					null,
				),
			).toBe('GET /users/profile')
		})
	})
})
