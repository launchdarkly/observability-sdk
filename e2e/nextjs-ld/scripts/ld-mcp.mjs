#!/usr/bin/env node
/* eslint-disable no-console */
/**
 * Minimal streamable-HTTP MCP client for the LaunchDarkly staging MCP server.
 *
 * Usage:
 *   node scripts/ld-mcp.mjs list-tools                  # dump tool schemas
 *   node scripts/ld-mcp.mjs call <tool> '<json-args>'   # call one tool
 *
 * Auth: reads the OAuth token stored by `pi mcp login launchdarkly-staging`
 * from ~/.pi/agent/mcp-auth.json.
 */
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const URL_LD = 'https://mcp.launchdarkly.com/mcp/staging'

function readToken() {
	const auth = JSON.parse(
		readFileSync(join(process.env.HOME, '.pi/agent/mcp-auth.json'), 'utf8'),
	)
	return Object.values(auth).find((v) =>
		v.serverUrl?.includes('launchdarkly'),
	).tokens.access_token
}

let rpcId = 0
let sessionId

/** One step of the streamable-HTTP MCP protocol. Notifications return null. */
async function rpc(method, params) {
	const res = await fetch(URL_LD, {
		method: 'POST',
		headers: {
			authorization: `Bearer ${readToken()}`,
			'content-type': 'application/json',
			accept: 'application/json, text/event-stream',
			...(sessionId ? { 'mcp-session-id': sessionId } : {}),
			'mcp-protocol-version': '2025-06-18',
		},
		body: JSON.stringify({ jsonrpc: '2.0', id: ++rpcId, method, params }),
	})
	const sid = res.headers.get('mcp-session-id')
	if (sid) sessionId = sid
	const raw = await res.text()
	// Notifications (no id in the response) are acknowledged with 202.
	if (method.startsWith('notifications/')) return null
	if (res.status !== 200) {
		throw new Error(`${method} -> ${res.status}: ${raw.slice(0, 300)}`)
	}
	let body = raw
	if (raw.startsWith('event:') || raw.includes('\ndata:')) {
		body = raw
			.split('\n')
			.filter((l) => l.startsWith('data:'))
			.map((l) => l.slice(5).trim())
			.join('')
	}
	const payload = JSON.parse(body)
	if (payload.error) {
		throw new Error(`${method} rpc error: ${payload.error.message}`)
	}
	return payload
}

await rpc('initialize', {
	protocolVersion: '2025-06-18',
	capabilities: {},
	clientInfo: { name: 'ld-mcp-e2e', version: '0.0.1' },
})
await rpc('notifications/initialized', {})

const [cmd, tool, argsJson] = process.argv.slice(2)

if (cmd === 'list-tools') {
	const { result } = await rpc('tools/list', {})
	for (const t of result.tools) {
		console.log(`\n=== ${t.name} ===`)
		if (t.description) console.log(String(t.description).slice(0, 500))
		console.log('args:', JSON.stringify(t.inputSchema?.required ?? []))
		console.log(
			'props:',
			JSON.stringify(t.inputSchema?.properties ?? {}, null, 1).slice(
				0,
				1500,
			),
		)
	}
} else if (cmd === 'call') {
	const { result } = await rpc('tools/call', {
		name: tool,
		arguments: argsJson ? JSON.parse(argsJson) : {},
	})
	if (result?.isError) {
		console.error('TOOL ERROR:', JSON.stringify(result.content))
		process.exit(2)
	}
	for (const c of result?.content ?? []) {
		if (c.type === 'text') console.log(c.text)
		else console.log(`[${c.type} content]`)
	}
} else {
	console.error('usage: ld-mcp.mjs list-tools | call <tool> <json-args>')
	process.exit(1)
}
