export type GraphQLOperationType = 'query' | 'mutation' | 'subscription'

/**
 * The identity of a GraphQL operation extracted from a request body.
 */
export interface GraphQLOperation {
	name?: string
	type?: GraphQLOperationType
}

// Matches the leading operation keyword and its optional name in a GraphQL
// document, e.g. `query GetUser`, `mutation($id: ID!)`, `subscription OnEvent`.
const OPERATION_RE = /\b(query|mutation|subscription)\b\s*([A-Za-z_]\w*)?/

/**
 * Best-effort extraction of a GraphQL operation's name and type from an
 * outgoing HTTP request body. Returns `null` when the body is not a GraphQL
 * request, so callers can cheaply skip non-GraphQL traffic.
 *
 * Handles the transports used by the common clients (Apollo, urql,
 * graphql-request):
 *   - a JSON object `{ query, operationName?, variables? }`
 *   - a batched array of those objects (the first entry is described)
 *   - an already-parsed object (not just a JSON string)
 *
 * Intentionally regex-based with no dependency on the `graphql` package: this
 * runs inside an OpenTelemetry instrumentation hook, so it must be synchronous,
 * exception-safe, and small enough to inline into the browser SDK's bundle.
 */
export function parseGraphQLOperation(body: unknown): GraphQLOperation | null {
	const payload = parseBody(body)
	if (payload === null || typeof payload !== 'object') {
		return null
	}

	// Batched requests send an array of operations; describe the first one.
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

	// A GraphQL request carries a document and/or an explicit operation name.
	// Anything else (e.g. a plain REST JSON body) is not a GraphQL operation.
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

// Accepts an already-parsed object/array verbatim, or parses a JSON string.
// Returns null for anything that isn't parseable JSON.
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

// Anonymous shorthand (`{ ... }`) has no operation keyword but is a query per
// the GraphQL spec.
function inferType(
	query: string | undefined,
): GraphQLOperationType | undefined {
	if (query !== undefined && /^\s*\{/.test(query)) {
		return 'query'
	}
	return undefined
}
