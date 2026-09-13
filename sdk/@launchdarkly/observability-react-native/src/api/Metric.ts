import { Attributes } from '@opentelemetry/api'

export interface Metric {
	name: string
	value: number
	/** UCUM / OTel unit for the instrument (e.g. `ms`, `By`). */
	unit?: string
	attributes?: Attributes
	timestamp?: number
}
