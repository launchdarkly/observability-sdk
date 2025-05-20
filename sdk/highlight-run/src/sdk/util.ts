export const recordWarning = (context: string, ...msg: any) => {
	const prefix = `[@launchdarkly plugins] warning: (${context}): `
	console.warn(prefix, ...msg)
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
