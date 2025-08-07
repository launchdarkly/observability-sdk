import { eventWithTime } from '@rrweb/types'
import { strToU8 } from 'fflate'
import { PayloadTrigger } from './types'

/**
 * Triggers payload sending based on accumulated byte size of events
 */
export class ByteSizePayloadTrigger implements PayloadTrigger {
	readonly name = 'byteSize'
	private currentByteSize: number = 0
	private callback?: () => void

	constructor(private byteSizeThreshold: number) {}

	beforeEventAdded(newEvent: eventWithTime): void {
		const newEventByteSize = this.calculateByteSize(newEvent)

		if (
			this.currentByteSize + newEventByteSize > this.byteSizeThreshold &&
			this.callback
		) {
			console.log(
				'TriggerReason: ByteSizePayloadTrigger beforeEventAdded',
			)
			this.callback()
		}
	}

	afterEventAdded(newEvent: eventWithTime): void {
		const newEventByteSize = this.calculateByteSize(newEvent)

		this.currentByteSize += newEventByteSize

		// Its possible that the new event itself is larger than the threshold, so we need to check again.
		if (this.currentByteSize > this.byteSizeThreshold && this.callback) {
			console.log('TriggerReason: ByteSizePayloadTrigger afterEventAdded')
			this.callback()
		}
	}

	resetTrigger(): void {
		console.log(
			'ByteSizePayloadTrigger, before reset bytes:',
			this.currentByteSize,
		)
		this.currentByteSize = 0
	}

	start(callback: () => void): void {
		this.callback = callback
		// TODO: reset the trigger?
	}

	stop(): void {
		this.callback = undefined
		this.resetTrigger()
	}

	private calculateByteSize(event: eventWithTime): number {
		return strToU8(JSON.stringify(event)).length
	}
}
