// WebSocket GraphQL client for testing connection reuse
export class WebSocketGraphQLClient {
	private ws: WebSocket | null = null
	private messageId = 1
	private isConnected = false
	private messageQueue: any[] = []

	constructor(private url: string) {}

	connect(): Promise<void> {
		return new Promise((resolve, reject) => {
			try {
				this.ws = new WebSocket(this.url, 'graphql-transport-ws')

				this.ws.onopen = () => {
					console.log('WebSocket connected')
					// Send connection init (required for graphql-transport-ws)
					this.ws!.send(
						JSON.stringify({
							type: 'connection_init',
							payload: {},
						}),
					)
				}

				this.ws.onmessage = (event) => {
					const data = JSON.parse(event.data)
					console.log('WebSocket received:', data)

					if (data.type === 'connection_ack') {
						this.isConnected = true
						console.log('WebSocket connection acknowledged')

						// Send any queued messages
						this.messageQueue.forEach((msg) =>
							this.ws!.send(JSON.stringify(msg)),
						)
						this.messageQueue = []

						resolve()
					}
				}

				this.ws.onclose = () => {
					console.log('WebSocket disconnected')
					this.isConnected = false
				}

				this.ws.onerror = (error) => {
					console.error('WebSocket error:', error)
					reject(error)
				}
			} catch (error) {
				reject(error)
			}
		})
	}

	sendMutation(query: string, variables?: any): Promise<any> {
		return this.sendOperation('mutation', query, variables)
	}

	sendQuery(query: string, variables?: any): Promise<any> {
		return this.sendOperation('query', query, variables)
	}

	private sendOperation(
		type: string,
		query: string,
		variables?: any,
	): Promise<any> {
		return new Promise((resolve) => {
			const id = String(this.messageId++)
			const message = {
				id,
				type: 'subscribe', // All operations use 'subscribe' in graphql-transport-ws
				payload: {
					query,
					variables,
				},
			}

			console.log(`Sending ${type} over WebSocket:`, message)

			if (this.isConnected && this.ws) {
				this.ws.send(JSON.stringify(message))
			} else {
				// Queue message if not connected yet
				this.messageQueue.push(message)
			}

			// For this demo, we'll resolve immediately
			// In a real implementation, you'd wait for the response
			setTimeout(() => resolve({ id, type }), 100)
		})
	}

	disconnect() {
		if (this.ws) {
			this.ws.close()
			this.ws = null
			this.isConnected = false
		}
	}
}

// Singleton instance
export const websocketClient = new WebSocketGraphQLClient(
	'ws://localhost:8082/public',
)
