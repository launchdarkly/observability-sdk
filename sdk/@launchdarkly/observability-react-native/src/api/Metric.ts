import { Attributes } from '@opentelemetry/api'

export interface Metric {
	name: string
	value: number
	attributes?: Attributes
	timestamp?: number
}
