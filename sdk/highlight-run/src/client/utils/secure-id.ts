import MurmurHash3 from 'imurmurhash'

const ID_LENGTH = 28
// base 62 character set
const CHARACTER_SET =
	'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'

const MIXER_PRIME = 0x85ebca6b

export const GenerateSecureID = (key?: string): string => {
	var secureID = ''

	if (key) {
		// UseMurmurHash3 for fast, high-quality deterministic hashing (note: not cryptographically secure)
		const keyHash = MurmurHash3(key).result()

		for (let i = 0; i < ID_LENGTH; i++) {
			const charHash = (keyHash ^ (i * MIXER_PRIME)) >>> 0
			const charIndex = charHash % CHARACTER_SET.length
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
