const ID_LENGTH = 28
// base 62 character set
const CHARACTER_SET =
	'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'

const simpleHash = (str: string): number => {
	let hash = 0x811c9dc5
	const prime = 0x01000193

	for (let i = 0; i < str.length; i++) {
		const char = str.charCodeAt(i)
		hash = (hash ^ char) * prime
		hash = hash >>> 0
	}

	// Additional mixing to improve distribution
	hash = hash ^ (hash >>> 16)
	hash = hash * 0x85ebca6b
	hash = hash ^ (hash >>> 13)
	hash = hash * 0xc2b2ae35
	hash = hash ^ (hash >>> 16)

	return hash >>> 0
}

export const GenerateSecureID = (key?: string): string => {
	var secureID = ''

	if (key) {
		const keyHash = simpleHash(key)
		for (let i = 0; i < ID_LENGTH; i++) {
			const characterHash = keyHash ^ i
			const charIndex = characterHash % CHARACTER_SET.length
			secureID += CHARACTER_SET.charAt(charIndex)
		}
	} else {
		const hasCrypto =
			typeof window !== 'undefined' && window.crypto?.getRandomValues
		const cryptoRandom = new Uint32Array(ID_LENGTH)
		if (hasCrypto) {
			window.crypto.getRandomValues(cryptoRandom)
		}

		for (let i = 0; i < ID_LENGTH; i++) {
			if (hasCrypto) {
				secureID += CHARACTER_SET.charAt(
					cryptoRandom[i] % CHARACTER_SET.length,
				)
			} else {
				secureID += CHARACTER_SET.charAt(
					Math.floor(Math.random() * CHARACTER_SET.length),
				)
			}
		}
	}

	return secureID
}
