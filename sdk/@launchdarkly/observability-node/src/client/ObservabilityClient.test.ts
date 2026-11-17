import { SemanticResourceAttributes } from '@opentelemetry/semantic-conventions'
import { ObservabilityClient } from './ObservabilityClient.js'
import type { Instrumentation } from '@opentelemetry/instrumentation'
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

	it('applies instrumentationConfig to the registered instrumentations', () => {
		const ignoreIncomingRequestHook = () => true
		const client = new ObservabilityClient('abc123', {
			instrumentationConfig: {
				'@opentelemetry/instrumentation-http': {
					ignoreIncomingRequestHook,
				},
				'@opentelemetry/instrumentation-dns': { enabled: false },
			},
		})

		const instrumentations: Instrumentation[] =
			client.otel['_instrumentations']
		const names = instrumentations.map((i) => i.instrumentationName)
		expect(names).not.toContain('@opentelemetry/instrumentation-dns')
		const http = instrumentations.find(
			(i) =>
				i.instrumentationName === '@opentelemetry/instrumentation-http',
		)
		expect(http?.getConfig()).toMatchObject({
			enabled: true,
			ignoreIncomingRequestHook,
		})
	})
})
