import { eventWithTime } from '@rrweb/types'

/**
 * Interface for implementing custom payload trigger strategies.
 * Each trigger manages its own state and calls the provided callback when it should fire.
 */
export interface PayloadTrigger {
	readonly name: string
	beforeEventAdded(newEvent: eventWithTime): void
	afterEventAdded(newEvent: eventWithTime): void
	resetTrigger(): void
	start(callback: () => void): void
	stop(): void
}

/**
 * Configuration options for payload triggers
 */
// TODO: Maybe delete this.
export interface PayloadTriggerConfig {
	timerMs: number
	byteSizeThreshold: number
}
