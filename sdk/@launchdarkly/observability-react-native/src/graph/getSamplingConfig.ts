interface MatchConfig {
	regexValue?: string
	matchValue?: string
}

interface AttributeConfig {
	key?: MatchConfig
	attribute?: MatchConfig
}

interface EventConfig {
	name?: MatchConfig
	attributes?: AttributeConfig[]
}

interface SpanSamplingConfig {
	name?: MatchConfig
	attributes?: AttributeConfig[]
	events?: EventConfig[]
	samplingRatio?: number
}

interface LogSamplingConfig {
	message?: MatchConfig
	severityText?: MatchConfig
	attributes?: AttributeConfig[]
	samplingRatio?: number
}

export interface SamplingConfig {
	spans?: SpanSamplingConfig[]
	logs?: LogSamplingConfig[]
}

const GET_SAMPLING_CONFIG_QUERY = `
	fragment MatchParts on MatchConfig {
		regexValue
		matchValue
	}

	query GetSamplingConfig($organization_verbose_id: String!) {
		sampling(organization_verbose_id: $organization_verbose_id) {
			spans {
				name {
					...MatchParts
				}
				attributes {
					key {
						...MatchParts
					}
					attribute {
						...MatchParts
					}
				}
				events {
					name {
						...MatchParts
					}
					attributes {
						key {
							...MatchParts
						}
						attribute {
							...MatchParts
						}
					}
				}
				samplingRatio
			}
			logs {
				message {
					...MatchParts
				}
				severityText {
					...MatchParts
				}
				attributes {
					key {
						...MatchParts
					}
					attribute {
						...MatchParts
					}
				}
				samplingRatio
			}
		}
	}
`

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
				query: GET_SAMPLING_CONFIG_QUERY,
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
