import { Attributes, Context } from '@opentelemetry/api'
import { LogRecordProcessor, SdkLogRecord } from '@opentelemetry/sdk-logs'

/**
 * Stamps the attributes set with `LDObserve.setAttributes` on every log
 * record. Keys the record already carries are left alone so per-call
 * attributes win. Must be registered before the exporting processor.
 */
export class GlobalAttributesLogRecordProcessor implements LogRecordProcessor {
	constructor(private readonly getAttributes: () => Attributes) {}

	onEmit(logRecord: SdkLogRecord, _context?: Context): void {
		const attributes = this.getAttributes()
		for (const key in attributes) {
			const value = attributes[key]
			if (
				value !== undefined &&
				logRecord.attributes[key] === undefined
			) {
				logRecord.setAttribute(key, value)
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
