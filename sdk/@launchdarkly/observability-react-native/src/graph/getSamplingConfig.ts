import { gql } from './generated/gql'
import { GetSamplingConfigDocument } from './generated/graphql'
import type { SamplingConfig } from './generated/graphql'

export async function getSamplingConfig(
	projectId: string,
	endpoint: string = 'https://pub-graph.highlight.io',
): Promise<SamplingConfig | null> {
	try {
		const response = await fetch(endpoint, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
			body: JSON.stringify({
				query: GetSamplingConfigDocument.loc?.source.body,
				variables: { project_id: projectId },
			}),
		})

		if (!response.ok) {
			console.warn(
				`Failed to fetch sampling config: ${response.status} ${response.statusText}`,
			)
			return null
		}

		const result = await response.json()

		if (result.errors) {
			console.warn('GraphQL errors in sampling config:', result.errors)
			return null
		}

		return result.data?.sampling_config || null
	} catch (error) {
		console.warn('Error fetching sampling config:', error)
		return null
	}
}
