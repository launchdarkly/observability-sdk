/**
 * Example usage of the generated GetSamplingConfig GraphQL query
 * This demonstrates how to use the standalone generated code with fetch
 */

import {
	executeGetSamplingConfig,
	createGetSamplingConfigBody,
	GetSamplingConfigQueryVariables,
} from './sampling-config-generated'

// Example 1: Using the helper function
async function exampleWithHelper() {
	try {
		const variables: GetSamplingConfigQueryVariables = {
			organization_verbose_id: 'your-organization-id',
		}

		const result = await executeGetSamplingConfig(
			'https://your-graphql-endpoint.com/graphql',
			variables,
			{
				headers: {
					Authorization: 'Bearer your-token',
				},
			},
		)

		console.log('Sampling config:', result.sampling)

		// Access specific parts of the response
		if (result.sampling.spans) {
			console.log('Span sampling configs:', result.sampling.spans.length)
		}

		if (result.sampling.logs) {
			console.log('Log sampling configs:', result.sampling.logs.length)
		}
	} catch (error) {
		console.error('Error fetching sampling config:', error)
	}
}

// Example 2: Using fetch directly with the generated body
async function exampleWithFetch() {
	try {
		const variables: GetSamplingConfigQueryVariables = {
			organization_verbose_id: 'your-organization-id',
		}

		const body = createGetSamplingConfigBody(variables)

		const response = await fetch(
			'https://your-graphql-endpoint.com/graphql',
			{
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					Authorization: 'Bearer your-token',
				},
				body: JSON.stringify(body),
			},
		)

		if (!response.ok) {
			throw new Error(`HTTP error! status: ${response.status}`)
		}

		const result = await response.json()

		if (result.errors) {
			throw new Error(`GraphQL errors: ${JSON.stringify(result.errors)}`)
		}

		console.log('Sampling config:', result.data.sampling)
	} catch (error) {
		console.error('Error fetching sampling config:', error)
	}
}

// Example 3: Manual fetch with the query string
async function exampleManualFetch() {
	try {
		const query = `
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
      }
    `

		const variables = {
			organization_verbose_id: 'your-organization-id',
		}

		const response = await fetch(
			'https://your-graphql-endpoint.com/graphql',
			{
				method: 'POST',
				headers: {
					'Content-Type': 'application/json',
					Authorization: 'Bearer your-token',
				},
				body: JSON.stringify({
					query,
					variables,
				}),
			},
		)

		const result = await response.json()
		console.log('Manual fetch result:', result.data.sampling)
	} catch (error) {
		console.error('Error with manual fetch:', error)
	}
}

// Run examples (uncomment to test)
// exampleWithHelper();
// exampleWithFetch();
// exampleManualFetch();

export { exampleWithHelper, exampleWithFetch, exampleManualFetch }
