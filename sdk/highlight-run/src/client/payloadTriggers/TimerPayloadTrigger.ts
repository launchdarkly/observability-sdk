import { eventWithTime } from '@rrweb/types'
import { PayloadTrigger } from './types'

/**
 * Triggers payload sending based on a timer interval.
 * This trigger is completely independent and doesn't need events to fire.
 */
export class TimerPayloadTrigger implements PayloadTrigger {
	readonly name = 'timer'
	private timerId?: ReturnType<typeof setTimeout>
	private callback?: () => void

	constructor(private intervalMs: number) {}

	onTriggered(): void {
		// Restart the timer after triggering
		if (this.callback) {
			this.start(this.callback)
		}
	}

	afterEventAdded(newEvent: eventWithTime): void {
		// Timer trigger doesn't care about individual events
	}

	beforeEventAdded(newEvent: eventWithTime): void {
		// Timer trigger doesn't care about individual events
	}

	resetTrigger(): void {
		if (this.timerId) {
			clearTimeout(this.timerId)
		}
		this.startTimer()
	}

	start(callback: () => void): void {
		this.callback = callback
		this.resetTrigger()
	}

	private startTimer(): void {
		this.timerId = setTimeout(() => {
			if (this.callback) {
				console.log('TriggerReason: TimerPayloadTrigger')
				this.callback()
			}
		}, this.intervalMs)
	}

	stop(): void {
		if (this.timerId) {
			clearTimeout(this.timerId)
			this.timerId = undefined
		}
	}
}
