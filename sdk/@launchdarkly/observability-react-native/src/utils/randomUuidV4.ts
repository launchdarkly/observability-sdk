// Mirrors highlight-run's `client/utils/randomUuidV4.ts` so the web and React
// Native SDKs generate session identifiers of comparable strength. Eventually
// this should live in a shared package (SDK-905).
//
// Generates RFC-4122 v4 UUIDs from a cryptographically-secure RNG
// (`crypto.getRandomValues`) whenever one is available:
//   - browser WebCrypto (React Native for Web),
//   - a native polyfill such as `react-native-get-random-values` or `expo-crypto`.
// It falls back to `Math.random()` only when no secure RNG is present, so id
// generation never hard-fails.
//
// We intentionally build the UUID from `getRandomValues` rather than calling
// `crypto.randomUUID()`: `randomUUID()` is missing in many React Native runtimes
// and, in browsers, only exists in secure contexts (https/localhost), whereas
// `getRandomValues` carries no such restriction.

// UUIDv4 field layout — https://www.rfc-archive.org/getrfc.php?rfc=4122 (Appendix A).
const timeLow = { start: 0, end: 3 }
const timeMid = { start: 4, end: 5 }
const timeHiAndVersion = { start: 6, end: 7 }
const clockSeqHiAndReserved = { start: 8, end: 8 }
const clockSeqLow = { start: 9, end: 9 }
const nodes = { start: 10, end: 15 }

type SecureRandomSource = {
	getRandomValues: (array: Uint8Array) => Uint8Array
}

function getSecureRandomSource(): SecureRandomSource | undefined {
	const candidate = (globalThis as { crypto?: SecureRandomSource }).crypto
	return candidate && typeof candidate.getRandomValues === 'function'
		? candidate
		: undefined
}

/**
 * Returns whether a cryptographically-secure RNG is available in this runtime.
 * When `false`, {@link randomUuidV4} degrades to `Math.random()`.
 */
export function isSecureRandomAvailable(): boolean {
	return getSecureRandomSource() !== undefined
}

function getRandom128bit(): number[] {
	const secure = getSecureRandomSource()
	if (secure) {
		const typedArray = new Uint8Array(16)
		secure.getRandomValues(typedArray)
		return [...typedArray.values()]
	}

	const values: number[] = []
	for (let index = 0; index < 16; index += 1) {
		// Math.random is [0, 1) with inclusive min and exclusive max.
		values.push(Math.floor(Math.random() * 256))
	}
	return values
}

function hex(bytes: number[], range: { start: number; end: number }): string {
	let strVal = ''
	for (let index = range.start; index <= range.end; index += 1) {
		strVal += bytes[index].toString(16).padStart(2, '0')
	}
	return strVal
}

/**
 * Given 16 random bytes, formats them as a v4 UUID string.
 *
 * Note: the input bytes are mutated in place to set the version (4) and variant
 * bits required by RFC-4122.
 *
 * @param bytes A list of 16 bytes.
 * @returns A UUID v4 string.
 */
export function formatDataAsUuidV4(bytes: number[]): string {
	// Set the variant: bits 6 and 7 of clock_seq_hi_and_reserved to 1 and 0.
	bytes[clockSeqHiAndReserved.start] =
		(bytes[clockSeqHiAndReserved.start] | 0x80) & 0xbf
	// Set the version: high nibble of time_hi_and_version to 4.
	bytes[timeHiAndVersion.start] = (bytes[timeHiAndVersion.start] & 0x0f) | 0x40

	return (
		`${hex(bytes, timeLow)}-${hex(bytes, timeMid)}-${hex(bytes, timeHiAndVersion)}-` +
		`${hex(bytes, clockSeqHiAndReserved)}${hex(bytes, clockSeqLow)}-${hex(bytes, nodes)}`
	)
}

/**
 * Generates an RFC-4122 v4 UUID, using a secure RNG when available and falling
 * back to `Math.random()` otherwise.
 */
export function randomUuidV4(): string {
	return formatDataAsUuidV4(getRandom128bit())
}
