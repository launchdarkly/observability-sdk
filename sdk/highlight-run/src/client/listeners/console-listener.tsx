import type { StackFrame } from 'error-stack-parser'
import { ALL_CONSOLE_METHODS, ConsoleMethods } from '../types/client'
import { ConsoleMessage } from '../types/shared-types'
import { patch, stringify } from '../utils/utils'
import { parseError } from '../utils/errors'

export type StringifyOptions = {
	// limit of string length
	stringLengthLimit?: number
	/**
	 * limit of number of keys in an object
	 * if an object contains more keys than this limit, we would call its toString function directly
	 */
	numOfKeysLimit: number
	/**
	 * limit number of depth in an object
	 * if an object is too deep, toString process may cause browser OOM
	 */
	depthOfLimit: number
}

export type LogRecordOptions = {
	level: ConsoleMethods[]
	stringifyOptions: StringifyOptions
	/**
	 * Set to try to serialize console object arguments into the message body.
	 */
	serializeConsoleAttributes?: boolean
	logger: Logger | 'console'
}

export type Logger = {
	assert?: typeof console.assert
	clear?: typeof console.clear
	count?: typeof console.count
	countReset?: typeof console.countReset
	debug?: typeof console.debug
	dir?: typeof console.dir
	dirxml?: typeof console.dirxml
	error?: typeof console.error
	group?: typeof console.group
	groupCollapsed?: typeof console.groupCollapsed
	groupEnd?: () => void
	info?: typeof console.info
	log?: typeof console.log
	table?: typeof console.table
	time?: typeof console.time
	timeEnd?: typeof console.timeEnd
	timeLog?: typeof console.timeLog
	trace?: typeof console.trace
	warn?: typeof console.warn
}

export const defaultLogOptions: LogRecordOptions = {
	level: [...ALL_CONSOLE_METHODS],
	logger: 'console',
	stringifyOptions: {
		depthOfLimit: 10,
		numOfKeysLimit: 100,
		stringLengthLimit: 1000,
	},
}

export function ConsoleListener(
	callback: (c: ConsoleMessage) => void,
	logOptions: LogRecordOptions,
) {
	const loggerType = logOptions.logger
	if (!loggerType) {
		return () => {}
	}
	let logger: Logger
	if (typeof loggerType === 'string') {
		logger = window[loggerType]
	} else {
		logger = loggerType
	}
	const cancelHandlers: (() => void)[] = []

	// add listener to thrown errors
	if (logOptions.level.includes('error')) {
		if (window) {
			const errorHandler = (event: ErrorEvent) => {
				const { message, error } = event
				let trace: StackFrame[] = []
				if (error) {
					trace = parseError(error)
				}
				const payload = [
					stringify(message, logOptions.stringifyOptions),
				]
				callback({
					type: 'Error',
					trace,
					time: Date.now(),
					value: payload,
				})
			}
			window.addEventListener('error', errorHandler)
			cancelHandlers.push(() => {
				if (window) window.removeEventListener('error', errorHandler)
			})
		}
	}

	for (const levelType of logOptions.level) {
		cancelHandlers.push(replace(logger, levelType))
	}
	return () => {
		cancelHandlers.forEach((h) => h())
	}

	/**
	 * replace the original console function and record logs
	 * @param logger the logger object such as Console
	 * @param level the name of log function to be replaced
	 */
	function replace(_logger: Logger, level: ConsoleMethods) {
		if (!_logger[level]) {
			return () => {}
		}
		// replace the logger.{level}. return a restore function
		return patch(_logger, level, (original) => {
			return (...data: Array<any>) => {
				// @ts-expect-error
				original.apply(this, data)
				try {
					callback(createLog(level, logOptions, ...data))
				} catch (error) {
					original('highlight logger error:', error, ...data)
				}
			}
		})
	}
}

export function createLog(
	level: string,
	logOptions: LogRecordOptions,
	...data: Array<any>
) {
	const trace = parseError(new Error())
	const message = logOptions.serializeConsoleAttributes
		? data.map((o) =>
				typeof o === 'object'
					? stringify(o, logOptions.stringifyOptions)
					: o,
			)
		: data.filter((o) => typeof o !== 'object').map((o) => `${o}`)
	return {
		type: level,
		trace,
		value: message,
		attributes: stringify(
			data
				.filter((d) => typeof d === 'object')
				.reduce((a, b) => ({ ...a, ...b }), {}),
			logOptions.stringifyOptions,
		),
		time: Date.now(),
	}
}
