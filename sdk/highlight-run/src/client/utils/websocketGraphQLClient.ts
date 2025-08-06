import { print, DocumentNode } from 'graphql'
import { createClient, Client as WSClient } from 'graphql-ws'
import { GraphQLClientRequestHeaders } from 'graphql-request/build/cjs/types'

// GraphQLClient adapter to make WebSocket client compatible with generated SDK
export interface GraphQLClientAdapter {
	request<T = any>(
		document: DocumentNode | string,
		variables?: any,
		requestHeaders?: GraphQLClientRequestHeaders,
	): Promise<T>
	// Add minimal stubs for other GraphQLClient properties that aren't used
	url?: string
	requestConfig?: any
}

/**
 * WebSocket GraphQL client wrapper that provides the same interface as graphql-request
 * but uses persistent WebSocket connections for better performance.
 */
export class WebSocketGraphQLClient implements GraphQLClientAdapter {
	private wsClient: WSClient
	private wsUrl: string

	// GraphQLClient compatibility - add required properties as stubs
	public readonly url: string
	public readonly requestConfig?: any

	constructor(url: string, options?: { headers?: Record<string, string> }) {
		// Convert HTTP URL to WebSocket URL
		this.wsUrl = url
			.replace(/^https?:\/\//, 'ws://')
			.replace(/^http:\/\//, 'ws://')

		// Set public url property for compatibility
		this.url = this.wsUrl

		this.wsClient = createClient({
			url: this.wsUrl,
			keepAlive: 10_000, // ping every 10 seconds
			lazyCloseTimeout: 20_000, // close connection after 20 seconds of inactivity
			connectionParams: options?.headers
				? { headers: options.headers }
				: undefined,
			on: {
				opened: () => {
					console.log('WebSocket GraphQL connection opened')
					//this.isConnected = true
				},
				closed: () => {
					console.log('WebSocket GraphQL connection closed')
					//this.isConnected = false
				},
				error: (error) => {
					console.error('WebSocket GraphQL error:', error)
				},
			},
		})
	}

	/**
	 * Send a GraphQL operation over WebSocket
	 * Maintains compatibility with graphql-request interface
	 */
	async request<TData = any>(
		document: DocumentNode | string,
		variables?: any,
		requestHeaders?: GraphQLClientRequestHeaders,
	): Promise<TData> {
		console.log('WebSocketGraphQLClient.request', document, variables)

		const query = typeof document === 'string' ? document : print(document)

		return new Promise((resolve, reject) => {
			let result: any = null
			let error: any = null

			this.wsClient.subscribe(
				{
					query,
					variables,
				},
				{
					next: (data) => {
						console.log('WebSocketGraphQLClient.request next', data)
						if (data.errors) {
							error = new Error(
								`GraphQL errors: ${JSON.stringify(data.errors)}`,
							)
						} else {
							result = data.data
						}
					},
					error: (err) => {
						console.error(
							'WebSocketGraphQLClient.request error',
							err,
						)
						reject(err)
					},
					complete: () => {
						console.log('WebSocketGraphQLClient.request complete')
						if (error) {
							reject(error)
						} else {
							resolve(result)
						}
					},
				},
			)
		})
	}

	/**
	 * Close the WebSocket connection
	 */
	dispose(): void {
		this.wsClient.dispose()
	}
}

/**
 * Create a WebSocket GraphQL client with the same interface as GraphQLClient from graphql-request
 */
export function createWebSocketGraphQLClient(
	url: string,
	options?: { headers?: Record<string, string> },
): WebSocketGraphQLClient {
	return new WebSocketGraphQLClient(url, options)
}
