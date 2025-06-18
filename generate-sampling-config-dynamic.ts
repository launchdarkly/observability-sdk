#!/usr/bin/env node

/**
 * Dynamic script to generate GraphQL body for GetSamplingConfigDocument
 * and its TypeScript types by reading the actual GraphQL files and schema.
 *
 * This script:
 * 1. Reads the GraphQL query file
 * 2. Reads the schema types
 * 3. Generates TypeScript types and query body
 * 4. Can be re-run when GraphQL files change
 *
 * Run with: npx tsx generate-sampling-config-dynamic.ts
 */

import * as fs from 'fs'
import * as path from 'path'

// Paths to the source files
const GRAPHQL_QUERY_PATH = path.join(
	__dirname,
	'sdk/highlight-run/src/client/graph/operators/query.gql',
)
const GRAPHQL_MUTATION_PATH = path.join(
	__dirname,
	'sdk/highlight-run/src/client/graph/operators/mutation.gql',
)
const SCHEMA_PATH = path.join(
	__dirname,
	'sdk/highlight-run/src/client/graph/generated/schemas.ts',
)

interface GraphQLFragment {
	name: string
	content: string
}

interface GraphQLQuery {
	name: string
	variables: string[]
	content: string
}

/**
 * Extracts fragments from GraphQL files
 */
function extractFragments(content: string): GraphQLFragment[] {
	const fragments: GraphQLFragment[] = []
	const fragmentRegex = /fragment\s+(\w+)\s+on\s+\w+\s*\{([^}]+)\}/g
	let match

	while ((match = fragmentRegex.exec(content)) !== null) {
		fragments.push({
			name: match[1],
			content: `fragment ${match[1]} on ${match[0].split('on ')[1].split(' {')[0]} {${match[2]}}`,
		})
	}

	return fragments
}

/**
 * Extracts queries from GraphQL files
 */
function extractQueries(content: string): GraphQLQuery[] {
	const queries: GraphQLQuery[] = []
	const queryRegex = /query\s+(\w+)\s*\(([^)]*)\)\s*\{([^}]+)\}/g
	let match

	while ((match = queryRegex.exec(content)) !== null) {
		const variableMatches = match[2].match(/\$(\w+):\s*(\w+)/g) || []
		const variables = variableMatches.map((v) => v.split(':')[0].trim())

		queries.push({
			name: match[1],
			variables,
			content: `query ${match[1]}(${match[2]}) {${match[3]}}`,
		})
	}

	return queries
}

/**
 * Expands fragments in a GraphQL query
 */
function expandFragments(query: string, fragments: GraphQLFragment[]): string {
	let expandedQuery = query

	for (const fragment of fragments) {
		const fragmentUsage = `...${fragment.name}`
		if (expandedQuery.includes(fragmentUsage)) {
			// Extract the fragment content without the fragment declaration
			const fragmentContent = fragment.content
				.replace(/fragment\s+\w+\s+on\s+\w+\s*\{/, '')
				.replace(/\}$/, '')
			expandedQuery = expandedQuery.replace(
				fragmentUsage,
				fragmentContent,
			)
		}
	}

	return expandedQuery
}

/**
 * Generates TypeScript types from the schema
 */
function generateTypesFromSchema(schemaContent: string): string {
	// Extract the relevant types from the schema
	const typeExtractions = [
		'MatchConfig',
		'AttributeMatchConfig',
		'SpanEventMatchConfig',
		'SpanSamplingConfig',
		'LogSamplingConfig',
		'SamplingConfig',
	]

	let typesOutput = ''

	for (const typeName of typeExtractions) {
		const typeRegex = new RegExp(
			`export\\s+type\\s+${typeName}\\s*=\\s*\\{([^}]+)\\}`,
		)
		const match = schemaContent.match(typeRegex)

		if (match) {
			// Clean up the type definition
			let typeDef = match[1]
				.replace(/__typename\?\?:\s*['"][^'"]*['"]/g, '') // Remove __typename
				.replace(
					/Scalars\['([^']+)'\]\['output'\]/g,
					(_, scalarType) => {
						// Map GraphQL scalars to TypeScript types
						switch (scalarType) {
							case 'String':
								return 'string'
							case 'Int':
								return 'number'
							case 'Float':
								return 'number'
							case 'Boolean':
								return 'boolean'
							case 'ID':
								return 'string'
							case 'Any':
								return 'any'
							case 'Timestamp':
								return 'any'
							default:
								return 'any'
						}
					},
				)
				.replace(
					/Scalars\['([^']+)'\]\['input'\]/g,
					(_, scalarType) => {
						// Map GraphQL input scalars to TypeScript types
						switch (scalarType) {
							case 'String':
								return 'string'
							case 'Int':
								return 'number'
							case 'Float':
								return 'number'
							case 'Boolean':
								return 'boolean'
							case 'ID':
								return 'string'
							case 'Any':
								return 'any'
							case 'Timestamp':
								return 'any'
							default:
								return 'any'
						}
					},
				)
				.replace(/Maybe<([^>]+)>/g, '$1 | null')
				.replace(/InputMaybe<([^>]+)>/g, '$1 | null')
				.replace(/Array<([^>]+)>/g, '$1[]')
				.trim()

			typesOutput += `export interface ${typeName} {\n`
			typesOutput += typeDef
				.split('\n')
				.map((line) => {
					const trimmed = line.trim()
					if (trimmed && !trimmed.startsWith('//')) {
						return `  ${trimmed}`
					}
					return ''
				})
				.filter(Boolean)
				.join('\n')
			typesOutput += '\n}\n\n'
		}
	}

	return typesOutput
}

/**
 * Generates the complete output file
 */
function generateOutput(queryContent: string, typesContent: string): string {
	const timestamp = new Date().toISOString()

	return `// Auto-generated by generate-sampling-config-dynamic.ts
// Generated on: ${timestamp}
// Source files:
// - ${GRAPHQL_QUERY_PATH}
// - ${GRAPHQL_MUTATION_PATH}
// - ${SCHEMA_PATH}

// GraphQL Query (expanded with fragments):
${queryContent
	.split('\n')
	.map((line) => `// ${line}`)
	.join('\n')}

// TypeScript Types
${typesContent}

// Query variables interface
export interface GetSamplingConfigQueryVariables {
  organization_verbose_id: string;
}

// Query response interface
export interface GetSamplingConfigQuery {
  sampling: SamplingConfig;
}

// GraphQL query as a string
export const GET_SAMPLING_CONFIG_QUERY = \`${queryContent}\`;

/**
 * Creates the request body for the GetSamplingConfig GraphQL query
 * @param variables - The query variables
 * @returns The request body object ready for JSON.stringify()
 */
export function createGetSamplingConfigBody(variables: GetSamplingConfigQueryVariables) {
  return {
    query: GET_SAMPLING_CONFIG_QUERY,
    variables
  };
}

/**
 * Executes the GetSamplingConfig query using fetch
 * @param url - The GraphQL endpoint URL
 * @param variables - The query variables
 * @param options - Additional fetch options
 * @returns Promise with the query response
 */
export async function executeGetSamplingConfig(
  url: string,
  variables: GetSamplingConfigQueryVariables,
  options: RequestInit = {}
): Promise<GetSamplingConfigQuery> {
  const body = createGetSamplingConfigBody(variables);
  
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
    body: JSON.stringify(body),
    ...options,
  });

  if (!response.ok) {
    throw new Error(\`GraphQL request failed: \${response.status} \${response.statusText}\`);
  }

  const result = await response.json();
  
  if (result.errors) {
    throw new Error(\`GraphQL errors: \${JSON.stringify(result.errors)}\`);
  }

  return result.data;
}
`
}

/**
 * Main function to generate the output
 */
function main() {
	try {
		console.log('Reading GraphQL files...')

		// Read the GraphQL files
		const queryContent = fs.readFileSync(GRAPHQL_QUERY_PATH, 'utf8')
		const mutationContent = fs.readFileSync(GRAPHQL_MUTATION_PATH, 'utf8')
		const schemaContent = fs.readFileSync(SCHEMA_PATH, 'utf8')

		console.log('Extracting fragments and queries...')

		// Extract fragments from mutation file (where MatchParts is defined)
		const fragments = extractFragments(mutationContent)
		console.log(
			`Found ${fragments.length} fragments:`,
			fragments.map((f) => f.name),
		)

		// Extract queries from query file
		const queries = extractQueries(queryContent)
		console.log(
			`Found ${queries.length} queries:`,
			queries.map((q) => q.name),
		)

		// Find the GetSamplingConfig query
		const samplingQuery = queries.find(
			(q) => q.name === 'GetSamplingConfig',
		)
		if (!samplingQuery) {
			throw new Error(
				'GetSamplingConfig query not found in GraphQL files',
			)
		}

		console.log('Expanding fragments in query...')

		// Expand fragments in the query
		const expandedQuery = expandFragments(samplingQuery.content, fragments)

		console.log('Generating TypeScript types...')

		// Generate types from schema
		const typesContent = generateTypesFromSchema(schemaContent)

		console.log('Generating output file...')

		// Generate the complete output
		const output = generateOutput(expandedQuery, typesContent)

		// Write to file
		const outputPath = path.join(
			process.cwd(),
			'sampling-config-generated.ts',
		)
		fs.writeFileSync(outputPath, output)

		console.log(
			`✅ Generated sampling config types and query at: ${outputPath}`,
		)
		console.log('\n📝 Usage example:')
		console.log(`
import { executeGetSamplingConfig } from './sampling-config-generated';

const result = await executeGetSamplingConfig(
  'https://your-graphql-endpoint.com/graphql',
  { organization_verbose_id: 'your-org-id' }
);
console.log(result.sampling);
    `)

		console.log('\n🔄 To regenerate when GraphQL files change, run:')
		console.log('npx tsx generate-sampling-config-dynamic.ts')
	} catch (error) {
		console.error('❌ Error generating sampling config:', error)
		process.exit(1)
	}
}

// Run if this file is executed directly
if (require.main === module) {
	main()
}

export { main as generateSamplingConfig }
