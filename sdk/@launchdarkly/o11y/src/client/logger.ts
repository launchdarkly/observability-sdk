import { isSdkDebugLogging, withoutConsoleRecording } from './utils/sdkLogger'

/**
 * Per-component debug logger. Like `sdkLog`, its output is SDK-internal: it
 * is written only in debug mode and never captured by the console recorder.
 */
export class Logger {
	debug: boolean | undefined
	name: string | undefined

	constructor(debug?: boolean, name?: string) {
		this.debug = debug
		this.name = name
	}

	private prefix() {
		let prefix = `[${Date.now()}]`
		if (this.name) {
			prefix += ` - ${this.name}`
		}
		return prefix
	}

	log(...data: any[]) {
		if (this.debug) {
			withoutConsoleRecording(() =>
				console.log.apply(console, [this.prefix(), ...data]),
			)
		}
	}

	warn(...data: any[]) {
		if (this.debug || isSdkDebugLogging()) {
			withoutConsoleRecording(() =>
				console.warn.apply(console, [this.prefix(), ...data]),
			)
		}
	}
}
