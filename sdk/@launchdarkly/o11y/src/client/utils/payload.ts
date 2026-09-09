import { compressSync, strToU8 } from 'fflate'
import { PushPayloadMutationVariables } from '../graph/generated/operations'

export async function payloadToBase64(payload: PushPayloadMutationVariables) {
	const buf = strToU8(JSON.stringify(payload))
	const compressed = compressSync(buf)
	// use a FileReader to generate a base64 data URI:
	const base64url = await new Promise<string>((r) => {
		const reader = new FileReader()
		reader.onload = () => r(reader.result as string)
		reader.readAsDataURL(new Blob([new Uint8Array(compressed)]))
	})
	// remove data:application/octet-stream;base64, prefix
	return {
		compressedBase64: base64url.slice(base64url.indexOf(',') + 1),
		compressedSize: compressed.length,
		bufferLength: buf.length,
	}
}
