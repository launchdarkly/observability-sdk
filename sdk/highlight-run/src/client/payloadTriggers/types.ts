import { eventWithTime } from '@rrweb/types'

/**
 * Context information provided to PayloadTriggers when checking if they should trigger
 */
export interface PayloadTriggerContext {
	events: eventWithTime[]
}

/**
 * Interface for implementing custom payload trigger strategies
 */
export interface PayloadTrigger {
	readonly name: string
	shouldTrigger(context: PayloadTriggerContext): boolean
	onTriggered(): void
	onEventsAdded(events: eventWithTime[]): void
	reset(): void
	start(callback: () => void): void
	stop(): void
}

/**
 * Configuration options for payload triggers
 */
// TODO: Maybe delete this.
export interface PayloadTriggerConfig {
	timerMs?: number
	byteSizeThreshold?: number
}
