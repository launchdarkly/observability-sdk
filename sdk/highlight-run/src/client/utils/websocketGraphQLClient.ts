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
	private isConnected = false
	private connectionPromise: Promise<void> | null = null

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
			connectionParams: options?.headers
				? { headers: options.headers }
				: undefined,
			on: {
				opened: () => {
					console.log('WebSocket GraphQL connection opened')
					this.isConnected = true
				},
				closed: () => {
					console.log('WebSocket GraphQL connection closed')
					this.isConnected = false
				},
				error: (error) => {
					console.error('WebSocket GraphQL error:', error)
				},
			},
		})
	}

	/**
	 * Ensure connection is established before sending operations
	 */
	private async ensureConnected(): Promise<void> {
		if (this.isConnected) {
			return
		}

		if (this.connectionPromise) {
			return this.connectionPromise
		}

		this.connectionPromise = new Promise((resolve, reject) => {
			const timeout = setTimeout(() => {
				reject(new Error('WebSocket connection timeout'))
			}, 10000) // 10 second timeout

			const checkConnection = () => {
				if (this.isConnected) {
					clearTimeout(timeout)
					this.connectionPromise = null
					resolve()
				} else {
					setTimeout(checkConnection, 100)
				}
			}
			checkConnection()
		})

		return this.connectionPromise
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
		await this.ensureConnected()

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
						if (data.errors) {
							error = new Error(
								`GraphQL errors: ${JSON.stringify(data.errors)}`,
							)
						} else {
							result = data.data
						}
					},
					error: (err) => {
						reject(err)
					},
					complete: () => {
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
		this.isConnected = false
		this.connectionPromise = null
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
