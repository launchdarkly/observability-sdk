/**
 * Simple unique ID generator for React Native
 * Generates IDs that are unique enough for session/device tracking
 * without requiring crypto or uuid dependencies
 */

/**
 * Generates a unique ID similar to UUID format but using simple randomization
 * Format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx (where x is random hex, y is 8,9,a,b)
 */
export function generateUniqueId(): string {
	const timestamp = Date.now().toString(16) // Convert to hex
	const random1 = Math.random().toString(16).substring(2, 10) // 8 chars
	const random2 = Math.random().toString(16).substring(2, 6) // 4 chars
	const random3 = Math.random().toString(16).substring(2, 6) // 4 chars
	const random4 = Math.random().toString(16).substring(2, 14) // 12 chars

	// Ensure we have enough padding
	const pad = (str: string, length: number) =>
		str.padEnd(length, '0').substring(0, length)

	return [
		pad(timestamp.substring(0, 8), 8),
		pad(random1.substring(0, 4), 4),
		'4' + pad(random2.substring(0, 3), 3), // Version 4 UUID format
		['8', '9', 'a', 'b'][Math.floor(Math.random() * 4)] +
			pad(random3.substring(0, 3), 3),
		pad(random4, 12),
	].join('-')
}

/**
 * Generates a shorter unique ID for cases where full UUID length isn't needed
 */
export function generateShortId(): string {
	const timestamp = Date.now().toString(36) // Base36 for shorter string
	const random = Math.random().toString(36).substring(2, 8)
	return `${timestamp}_${random}`
}
