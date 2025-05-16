import { LDObserve } from './LDObserve'

export const recordWarning = (context: string, ...msg: any) => {
	const prefix = `[@launchdarkly plugins] warning: (${context}): `
	console.warn(prefix, msg)
	LDObserve.recordLog(`${prefix}${msg}`, 'warn')
}
