import { Headers } from './models'

const DEFAULT_BODY_LIMIT = 64 * 1024 // KB
const BODY_SIZE_LIMITS = {
	'application/json': 64 * 1024 * 1024, // MB
	'text/plain': 64 * 1024 * 1024, // MB
} as const

export const getBodyThatShouldBeRecorded = (
	bodyData: any,
	bodyKeysToRedact?: string[],
	bodyKeysToRecord?: string[],
	headers?: Headers | { [key: string]: string },
) => {
	let bodyLimit: number = DEFAULT_BODY_LIMIT
	if (headers) {
		let contentType: string = ''
		if (typeof headers['get'] === 'function') {
			contentType = headers.get('content-type') ?? ''
		} else {
			contentType = headers['content-type'] ?? ''
		}
		try {
			contentType = contentType.split(';')[0]
		} catch {}
		bodyLimit =
			BODY_SIZE_LIMITS[contentType as keyof typeof BODY_SIZE_LIMITS] ??
			DEFAULT_BODY_LIMIT
	}

	if (bodyData) {
		if (bodyKeysToRedact) {
			try {
				const json = JSON.parse(bodyData)

				if (Array.isArray(json)) {
					json.forEach((element) => {
						Object.keys(element).forEach((key) => {
							if (
								bodyKeysToRedact.includes(
									key.toLocaleLowerCase(),
								)
							) {
								element[key] = '[REDACTED]'
							}
						})
					})
				} else {
					Object.keys(json).forEach((key) => {
						if (
							bodyKeysToRedact.includes(key.toLocaleLowerCase())
						) {
							json[key] = '[REDACTED]'
						}
					})
				}

				bodyData = JSON.stringify(json)
			} catch {}
		}

		if (bodyKeysToRecord) {
			try {
				const json = JSON.parse(bodyData)

				Object.keys(json).forEach((key) => {
					if (!bodyKeysToRecord.includes(key.toLocaleLowerCase())) {
						json[key] = '[REDACTED]'
					}
				})

				bodyData = JSON.stringify(json)
			} catch {}
		}
	}

	try {
		bodyData = bodyData.slice(0, bodyLimit)
	} catch {}

	return bodyData
}
