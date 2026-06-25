export type GraphQLOperationType = 'query' | 'mutation' | 'subscription'

export interface GraphQLOperation {
	name?: string
	type?: GraphQLOperationType
}

// Operation keyword + optional name, e.g. `query GetUser`. Global so we can
// scan every operation in a multi-operation document.
const OPERATION_RE = /\b(query|mutation|subscription)\b\s*([A-Za-z_]\w*)?/g

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

	const match = query ? matchOperation(query, operationName) : null
	const type =
		(match?.[1] as GraphQLOperationType | undefined) ?? inferType(query)
	const name = operationName ?? match?.[2]

	if (name === undefined && type === undefined) {
		return null
	}

	return { name, type }
}

// Finds the relevant operation in the document. When operationName is given,
// returns that specific operation so the type reflects the named operation in a
// multi-operation document (which may mix queries and mutations); otherwise, or
// when the named operation isn't found, returns the first operation.
function matchOperation(
	query: string,
	operationName: string | undefined,
): RegExpExecArray | null {
	OPERATION_RE.lastIndex = 0
	let first: RegExpExecArray | null = null
	for (
		let match = OPERATION_RE.exec(query);
		match !== null;
		match = OPERATION_RE.exec(query)
	) {
		if (operationName === undefined || match[2] === operationName) {
			return match
		}
		if (first === null) {
			first = match
		}
	}
	return first
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
