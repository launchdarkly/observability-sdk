import { eventWithTime } from '@rrweb/types'
import { PayloadTrigger, PayloadTriggerContext } from './types'

/**
 * Triggers payload sending based on a timer interval.
 * This trigger is completely independent and doesn't need events to fire.
 */
export class TimerPayloadTrigger implements PayloadTrigger {
	readonly name = 'timer'
	private timerId?: ReturnType<typeof setTimeout>
	private callback?: () => void

	constructor(private intervalMs: number) {}

	shouldTrigger(context: PayloadTriggerContext): boolean {
		// Timer trigger is independent - it handles its own timing via setTimeout
		// We return false here because this trigger fires via its own timer, not event-based checks
		return false
	}

	onTriggered(): void {
		// Restart the timer after triggering
		this.startTimer()
	}

	onEventsAdded(events: eventWithTime[]): void {
		// Timer trigger doesn't care about individual events
	}

	reset(): void {
		if (this.timerId) {
			clearTimeout(this.timerId)
			this.timerId = undefined
		}
	}

	start(callback: () => void): void {
		this.callback = callback
		this.reset()
		this.timerId = setTimeout(() => {
			if (this.callback) {
				this.callback()
			}
		}, this.intervalMs)
	}

	stop(): void {
		this.reset()
	}
}
