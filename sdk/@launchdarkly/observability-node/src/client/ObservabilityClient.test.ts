import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions'
import { ObservabilityClient } from './ObservabilityClient.js'
import { describe, expect, it } from 'vitest'

describe('client', () => {
	it('includes process information in the resource', () => {
		const client = new ObservabilityClient('abc123')

		const sdk = client.otel
		const resource = sdk['_resource']

		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_NAME
			],
		).toEqual('nodejs')
		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_DESCRIPTION
			],
		).toEqual('Node.js')
		expect(
			resource.attributes[
				SemanticResourceAttributes.PROCESS_RUNTIME_VERSION
			],
		).toBeDefined()
	})

	it('includes service config if provided', () => {
		const client = new ObservabilityClient('abc123', {
			serviceName: 'my-service',
			serviceVersion: 'abc123',
		})

		const sdk = client.otel
		const resource = sdk['_resource']

		expect(
			resource.attributes[SemanticResourceAttributes.SERVICE_NAME],
		).toEqual('my-service')
		expect(
			resource.attributes[SemanticResourceAttributes.SERVICE_VERSION],
		).toEqual('abc123')
	})
})
