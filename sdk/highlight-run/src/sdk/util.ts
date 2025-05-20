export const internalLog = (
	context: string,
	level: keyof Console,
	...msg: any
) => {
	const prefix = `[@launchdarkly plugins]: (${context}): `
	console[level].apply(console, [prefix, ...msg])
	void reportLog(prefix, ...msg)
}

const reportLog = async (prefix: string, ...msg: any) => {
	try {
		const { LDObserve } = await import('./LDObserve')
		LDObserve.recordLog(`${prefix}${msg}`, 'warn')
	} catch (e) {
		// ignore error  reporting log
	}
}
