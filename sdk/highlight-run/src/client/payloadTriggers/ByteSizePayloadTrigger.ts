import { eventWithTime } from '@rrweb/types'
import { PayloadTrigger, PayloadTriggerContext } from './types'

/**
 * Triggers payload sending based on accumulated byte size of events
 */
export class ByteSizePayloadTrigger implements PayloadTrigger {
	readonly name = 'byteSize'
	private currentByteSize: number = 0

	constructor(private byteSizeThreshold: number) {}

	shouldTrigger(context: PayloadTriggerContext): boolean {
		return this.currentByteSize >= this.byteSizeThreshold
	}

	onTriggered(): void {
		this.reset()
	}

	onEventsAdded(events: eventWithTime[]): void {
		// Calculate the byte size of the new events
		const eventsJson = JSON.stringify(events)
		this.currentByteSize += new Blob([eventsJson]).size
	}

	reset(): void {
		this.currentByteSize = 0
	}

	start(callback: () => void): void {
		// Byte size trigger doesn't need to start anything
		// It's event-driven, not timer-driven
	}

	stop(): void {
		// Reset the accumulated byte size when stopping
		this.reset()
	}
}
