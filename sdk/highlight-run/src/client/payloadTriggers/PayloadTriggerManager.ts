import { eventWithTime } from '@rrweb/types'
import { PayloadTrigger, PayloadTriggerConfig } from './types'
import { TimerPayloadTrigger } from './TimerPayloadTrigger'
import { ByteSizePayloadTrigger } from './ByteSizePayloadTrigger'

/**
 * Manages multiple PayloadTriggers and coordinates their execution
 */
export class PayloadTriggerManager {
	private triggers: PayloadTrigger[] = []

	constructor(config: PayloadTriggerConfig) {
		this.triggers.push(new TimerPayloadTrigger(config.timerMs))
		this.triggers.push(new ByteSizePayloadTrigger(config.byteSizeThreshold))
	}

	/**
	 * Start all triggers
	 */
	start(callback: () => void): void {
		this.triggers.forEach((trigger) => trigger.start(callback))
	}

	/**
	 * Stop all triggers
	 */
	stop(): void {
		this.triggers.forEach((trigger) => trigger.stop())
	}

	/**
	 * Check triggers before adding an event (for pre-validation)
	 */
	beforeEventAdded(newEvent: eventWithTime): void {
		this.triggers.forEach((trigger) => trigger.beforeEventAdded(newEvent))
	}

	/**
	 * Process event after it's been added to the events array
	 */
	afterEventAdded(newEvent: eventWithTime): void {
		this.triggers.forEach((trigger) => trigger.afterEventAdded(newEvent))
	}

	/**
	 * Reset all triggers when a send payload has been triggered/sent
	 */
	resetTriggers(): void {
		this.triggers.forEach((trigger) => trigger.resetTrigger())
	}
}
