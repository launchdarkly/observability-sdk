import { Context } from '@opentelemetry/api'
import { LogRecordProcessor, SdkLogRecord } from '@opentelemetry/sdk-logs'
import { GlobalAttributes } from './GlobalAttributes'

/**
 * Fills global attributes onto each log record. Attributes already set on the
 * record (e.g. passed to `recordLog`) take precedence.
 */
export class GlobalAttributesLogRecordProcessor implements LogRecordProcessor {
	constructor(private readonly store: GlobalAttributes) {}

	onEmit(logRecord: SdkLogRecord, _context?: Context): void {
		const attributes = this.store.getAttributes()
		for (const key in attributes) {
			if (logRecord.attributes[key] === undefined) {
				logRecord.setAttribute(key, attributes[key])
			}
		}
	}

	forceFlush(): Promise<void> {
		return Promise.resolve()
	}

	shutdown(): Promise<void> {
		return Promise.resolve()
	}
}
