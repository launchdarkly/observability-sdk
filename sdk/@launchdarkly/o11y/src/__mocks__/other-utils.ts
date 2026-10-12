// Mock implementations for various utilities used in tests

// Storage utils
export const setItem = (key: string, value: string) => {
	try {
		window.localStorage.setItem(key, value)
	} catch (err) {
		console.error('Error setting localStorage item:', err)
	}
}

export const getItem = (key: string): any => {
	try {
		const value = window.localStorage.getItem(key)
		if (!value) return null

		try {
			return JSON.parse(value)
		} catch {
			return value
		}
	} catch (err) {
		console.error('Error getting localStorage item:', err)
		return null
	}
}

export const removeItem = (key: string) => {
	try {
		window.localStorage.removeItem(key)
	} catch (err) {
		console.error('Error removing localStorage item:', err)
	}
}

// Error utils
export const formatError = (error: any) => {
	if (!(error instanceof Error)) {
		return {
			name: 'Unknown Error',
			message: error.message || String(error),
			stack: '',
		}
	}

	return {
		name: error.name,
		message: error.message,
		stack: error.stack || '',
	}
}

// Privacy utils
export const shouldRedact = (key: string): boolean => {
	const sensitiveKeys = [
		'password',
		'token',
		'secret',
		'api_key',
		'api_secret',
		'access_token',
		'auth',
		'credential',
		'credit_card',
	]

	return sensitiveKeys.some((pattern) =>
		key.toLowerCase().includes(pattern.toLowerCase()),
	)
}

export const maskValue = (
	value: any,
	customMasker?: (value: any) => string,
): string => {
	if (customMasker) {
		return customMasker(value)
	}
	return '[REDACTED]'
}

// Secure ID utils
let secureIdCache: string | null = null

export const getSecureID = (): string => {
	if (secureIdCache) {
		return secureIdCache
	}

	secureIdCache = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(
		/[xy]/g,
		(c) => {
			const r = (Math.random() * 16) | 0
			const v = c === 'x' ? r : (r & 0x3) | 0x8
			return v.toString(16)
		},
	)

	return secureIdCache
}

// Graph utils
export const request = async ({
	url,
	query,
	variables,
}: {
	url: string
	query: string
	variables?: Record<string, any>
}): Promise<any> => {
	const response = await fetch(url, {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json',
		},
		body: JSON.stringify({
			query,
			variables,
		}),
	})

	if (!response.ok) {
		throw new Error(
			`GraphQL request failed: ${response.status} ${response.statusText}`,
		)
	}

	return response.json()
}
