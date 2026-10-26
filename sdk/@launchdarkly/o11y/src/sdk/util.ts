import { sdkLog } from '../client/utils/sdkLogger'

// Object for tracking previously logged messages.
const loggedIds = Object.create(null)

/**
 * SDK-internal diagnostic. Reaches the developer console only in debug mode
 * and is never recorded as one of the customer's log records (it used to be
 * both printed unconditionally and shipped as a `warn` log into the
 * customer's project).
 */
export const internalLog = (
	context: string,
	level: keyof Console,
	...msg: any
) => {
	const prefix = `[@launchdarkly plugins]: (${context}): `
	sdkLog(level as string, prefix, ...msg)
}

/**
 * Utility to help avoid logging the same message multiple times.
 *
 * This is helpful to prevent spamming the logs with sticky error conditions.
 * For example if local storage cannot be written to, then each attempt to
 * write would fail, and logging each time would spam the logs.
 *
 * @param logOnceId - A message with this ID will only be logged one time for the given context.
 * @param context - The context of the message.
 * @param level - The level of the message.
 * @param msg - The message to log.
 */
export const internalLogOnce = (
	context: string,
	logOnceId: string,
	level: keyof Console,
	...msg: any
) => {
	if (loggedIds[`${context}-${logOnceId}`]) {
		return
	}
	loggedIds[`${context}-${logOnceId}`] = true
	internalLog(context, level, ...msg)
}
