import { LDObserve } from './LDObserve'

export const recordWarning = (context: string, ...msg: any) => {
	console.warn(`[@launchdarkly plugins] warning: (${context}): `, msg)
	LDObserve.recordLog(`${msg}`, 'warn')
}
