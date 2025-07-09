export type Maybe<T> = T | null

export type MatchConfig = {
	__typename?: 'MatchConfig'
	regexValue?: Maybe<string>
	matchValue?: any
}

export type AttributeMatchConfig = {
	__typename?: 'AttributeMatchConfig'
	key: MatchConfig
	attribute: MatchConfig
}

export type SpanEventMatchConfig = {
	__typename?: 'SpanEventMatchConfig'
	attributes?: Maybe<Array<AttributeMatchConfig>>
	name?: Maybe<MatchConfig>
}

export type SpanSamplingConfig = {
	__typename?: 'SpanSamplingConfig'
	attributes?: Maybe<Array<AttributeMatchConfig>>
	events?: Maybe<Array<SpanEventMatchConfig>>
	name?: Maybe<MatchConfig>
	samplingRatio: number
}

export type LogSamplingConfig = {
	__typename?: 'LogSamplingConfig'
	attributes?: Maybe<Array<AttributeMatchConfig>>
	message?: Maybe<MatchConfig>
	severityText?: Maybe<MatchConfig>
	samplingRatio: number
}

export type SamplingConfig = {
	__typename?: 'SamplingConfig'
	logs?: Maybe<Array<LogSamplingConfig>>
	spans?: Maybe<Array<SpanSamplingConfig>>
}

const GetSamplingConfigQuery = `
query GetSamplingConfig($organization_verbose_id: String!) {
	sampling(organization_verbose_id: $organization_verbose_id) {
		spans {
			name {
				regexValue
				matchValue
			}
			attributes {
				key {
					regexValue
					matchValue
				}
				attribute {
					regexValue
					matchValue
				}
			}
			events {
				name {
					regexValue
					matchValue
				}
				attributes {
					key {
						regexValue
						matchValue
					}
					attribute {
						regexValue
						matchValue
					}
				}
			}
			samplingRatio
		}
		logs {
			message {
				regexValue
				matchValue
			}
			severityText {
				regexValue
				matchValue
			}
			attributes {
				key {
					regexValue
					matchValue
				}
				attribute {
					regexValue
					matchValue
				}
			}
			samplingRatio
		}
	}
}`

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
				query: GetSamplingConfigQuery,
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
