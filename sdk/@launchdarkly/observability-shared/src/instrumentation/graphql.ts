export type GraphQLOperationType = 'query' | 'mutation' | 'subscription'

export interface GraphQLOperation {
	name?: string
	type?: GraphQLOperationType
}

// Leading operation keyword + optional name, e.g. `query GetUser`.
const OPERATION_RE = /\b(query|mutation|subscription)\b\s*([A-Za-z_]\w*)?/

// Best-effort extraction of a GraphQL operation from a request body (Apollo,
// urql, graphql-request shapes). Returns null for non-GraphQL bodies. Regex
// based, no `graphql` dep, so it stays cheap and bundle-friendly.
export function parseGraphQLOperation(body: unknown): GraphQLOperation | null {
	const payload = parseBody(body)
	if (payload === null || typeof payload !== 'object') {
		return null
	}

	// Batched requests send an array; describe the first operation.
	const operation = Array.isArray(payload) ? payload[0] : payload
	if (!operation || typeof operation !== 'object') {
		return null
	}

	const record = operation as Record<string, unknown>
	const query = typeof record.query === 'string' ? record.query : undefined
	const operationName =
		typeof record.operationName === 'string'
			? record.operationName
			: undefined

	if (query === undefined && operationName === undefined) {
		return null
	}

	const match = query ? OPERATION_RE.exec(query) : null
	const type =
		(match?.[1] as GraphQLOperationType | undefined) ?? inferType(query)
	const name = operationName ?? match?.[2]

	if (name === undefined && type === undefined) {
		return null
	}

	return { name, type }
}

// Parses a JSON string, or passes an already-parsed object/array through.
function parseBody(body: unknown): unknown {
	if (body === null || body === undefined) {
		return null
	}
	if (typeof body === 'object') {
		return body
	}
	if (typeof body !== 'string') {
		return null
	}
	try {
		return JSON.parse(body)
	} catch {
		return null
	}
}

// Anonymous shorthand (`{ ... }`) is a query.
function inferType(
	query: string | undefined,
): GraphQLOperationType | undefined {
	if (query !== undefined && /^\s*\{/.test(query)) {
		return 'query'
	}
	return undefined
}
