import ErrorStackParser from 'error-stack-parser'
import { sdkLog } from './sdkLogger'

export function parseError(err: Error) {
	try {
		return ErrorStackParser.parse(err)
	} catch (originalError) {
		try {
			return ErrorStackParser.parse(new Error())
		} catch (secondaryError) {
			sdkLog('warn', `Highlight Warning: failed to parse error`, {
				originalError,
				secondaryError,
			})
			return []
		}
	}
}
