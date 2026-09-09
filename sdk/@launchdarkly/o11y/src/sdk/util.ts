// Object for tracking previously logged messages.
const loggedIds = Object.create(null)

export const internalLog = (
	context: string,
	level: keyof Console,
	...msg: any
) => {
	const prefix = `[@launchdarkly plugins]: (${context}): `
	console[level].apply(console, [prefix, ...msg])
	void reportLog(prefix, ...msg)
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

const reportLog = async (prefix: string, ...msg: any) => {
	try {
		const { LDObserve } = await import('./LDObserve')
		LDObserve.recordLog(`${prefix}${msg}`, 'warn')
	} catch (e) {
		// ignore error  reporting log
	}
}
