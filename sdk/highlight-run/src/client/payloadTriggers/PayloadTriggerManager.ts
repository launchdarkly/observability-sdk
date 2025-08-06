import { eventWithTime } from '@rrweb/types'
import { PayloadTrigger, PayloadTriggerContext } from './types'

/**
 * Manages multiple PayloadTriggers and coordinates their execution
 */
export class PayloadTriggerManager {
	private triggers: PayloadTrigger[] = []

	/**
	 * Add a trigger to the manager
	 */
	addTrigger(trigger: PayloadTrigger): void {
		this.triggers.push(trigger)
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
	 * Check if any triggers should fire based on current state
	 */
	checkShouldTrigger(events: eventWithTime[]): boolean {
		const context: PayloadTriggerContext = {
			events,
		}

		return this.triggers.some((trigger) => trigger.shouldTrigger(context))
	}

	/**
	 * Notify all triggers that events have been added
	 */
	notifyEventsAdded(events: eventWithTime[]): void {
		this.triggers.forEach((trigger) => trigger.onEventsAdded(events))
	}

	/**
	 * Notify all triggers that a payload has been triggered/sent
	 */
	notifyTriggered(): void {
		this.triggers.forEach((trigger) => trigger.onTriggered())
	}
}
