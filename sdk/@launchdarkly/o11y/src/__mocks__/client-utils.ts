// Mock implementations for client utils functions used in tests

export const generateUUID = (): string => {
	return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
		const r = (Math.random() * 16) | 0
		const v = c === 'x' ? r : (r & 0x3) | 0x8
		return v.toString(16)
	})
}

export const isObject = (value: any): boolean => {
	return Object.prototype.toString.call(value) === '[object Object]'
}

export const safeStringify = (obj: any): string => {
	// Handle circular references
	const seen = new WeakSet()
	return JSON.stringify(obj, (key, value) => {
		if (typeof value === 'object' && value !== null) {
			if (seen.has(value)) {
				return '[Circular]'
			}
			seen.add(value)
		}
		return value
	})
}

export const getBrowserDetails = () => {
	return {
		userAgent: window.navigator.userAgent,
		platform: navigator.platform,
		language: navigator.language,
	}
}

export const parseUrl = (url: string) => {
	const parser = document.createElement('a')
	parser.href = url
	return {
		protocol: parser.protocol,
		hostname: parser.hostname,
		pathname: parser.pathname,
		search: parser.search,
		hash: parser.hash,
	}
}

export const isExternalUrl = (url: string): boolean => {
	if (!url.startsWith('http')) return false

	const parser = document.createElement('a')
	parser.href = url
	return parser.hostname !== window.location.hostname
}

export const getUrlPath = (): string => {
	return window.location.pathname + window.location.search
}

export const sanitizeObjectForValues = (obj: any): any => {
	const sensitiveKeys = [
		'password',
		'token',
		'auth',
		'key',
		'secret',
		'credential',
		'credit',
		'card',
	]

	if (!obj || typeof obj !== 'object') return obj

	const result: any = Array.isArray(obj) ? [] : {}

	for (const key in obj) {
		const value = obj[key]

		// Check if key is sensitive
		const isSensitive = sensitiveKeys.some((k) =>
			key.toLowerCase().includes(k),
		)

		if (isSensitive) {
			result[key] = '[REDACTED]'
		} else if (typeof value === 'object' && value !== null) {
			result[key] = sanitizeObjectForValues(value)
		} else {
			result[key] = value
		}
	}

	return result
}

export const debounce = (fn: Function, delay: number) => {
	let timeoutId: any
	return function (this: any, ...args: any[]) {
		clearTimeout(timeoutId)
		timeoutId = setTimeout(() => fn.apply(this, args), delay)
	}
}
