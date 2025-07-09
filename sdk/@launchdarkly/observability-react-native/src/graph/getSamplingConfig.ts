import {
	SamplingConfig,
	SpanSamplingConfig,
	LogSamplingConfig,
	MatchConfig,
	AttributeMatchConfig,
	SpanEventMatchConfig,
	Maybe,
	GetSamplingConfigDocument,
	GetSamplingConfigQuery,
	GetSamplingConfigQueryVariables,
} from './generated/graphql'

export async function getSamplingConfig(
	organizationVerboseId: string,
): Promise<SamplingConfig | null> {
	const endpoint = 'https://pub.observability.app.launchdarkly.com'
	try {
		const response = await fetch(endpoint, {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json',
			},
		body: JSON.stringify({
			query: GetSamplingConfigDocument.toString(),
			variables: { organization_verbose_id: organizationVerboseId },
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

		return result.data?.sampling || null
	} catch (error) {
		console.warn('Error fetching sampling config:', error)
		return null
	}
}
