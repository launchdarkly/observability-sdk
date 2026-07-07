import { randomUuidV4 } from './randomUuidV4'

/**
 * Generates a session/device identifier as an RFC-4122 v4 UUID.
 *
 * Backed by a cryptographically-secure RNG (`crypto.getRandomValues`) whenever
 * one is available — browser WebCrypto for React Native for Web, or a polyfill
 * such as `react-native-get-random-values` / `expo-crypto` on native — and falls
 * back to `Math.random()` only when no secure RNG is present.
 *
 * This replaces an earlier `Math.random()`-only, timestamp-prefixed scheme:
 * that leaked session creation time in the first 32 bits and produced
 * predictable, non-uniformly-random ids.
 */
export function generateUniqueId(): string {
	return randomUuidV4()
}

/**
 * Generates a shorter unique ID for cases where full UUID length isn't needed.
 */
export function generateShortId(): string {
	const timestamp = Date.now().toString(36) // Base36 for shorter string
	const random = Math.random().toString(36).substring(2, 8)
	return `${timestamp}_${random}`
}
