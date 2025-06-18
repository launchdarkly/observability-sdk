import {
	GetSamplingConfigDocument,
	GetSamplingConfigQuery,
	SamplingConfig,
} from './generated/graphql'
import * as http from 'http'
import * as https from 'https'
import { URL } from 'url'

export async function getSamplingConfig(
	url: string,
	organizationVerboseId: string,
): Promise<SamplingConfig> {
	return new Promise((resolve, reject) => {
		const parsedUrl = new URL(url)
		const isHttps = parsedUrl.protocol === 'https:'
		const client = isHttps ? https : http

		const postData = JSON.stringify({
			query: GetSamplingConfigDocument.toString(),
			variables: {
				organization_verbose_id: organizationVerboseId,
			},
		})

		const options = {
			hostname: parsedUrl.hostname,
			port: parsedUrl.port || (isHttps ? 443 : 80),
			path: parsedUrl.pathname + parsedUrl.search,
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
				'Content-Length': Buffer.byteLength(postData),
			},
		}

		const req = client.request(options, (res) => {
			let data = ''

			res.on('data', (chunk) => {
				data += chunk
			})

			res.on('end', () => {
				try {
					const result = JSON.parse(data)
					resolve(result.data as SamplingConfig)
				} catch (error) {
					reject(new Error(`Failed to parse response: ${error}`))
				}
			})
		})

		req.on('error', (error) => {
			reject(error)
		})

		req.write(postData)
		req.end()
	})
}
