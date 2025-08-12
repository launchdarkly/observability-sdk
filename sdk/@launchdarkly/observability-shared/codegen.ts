import { type CodegenConfig } from '@graphql-codegen/cli'

const config: CodegenConfig = {
	schema: '../../../../observability/backend/public-graph/graph/schema.graphqls',
	documents: ['src/**/*.gql'],
	generates: {
		'./src/graph/generated/': {
			preset: 'client',
			config: {
				documentMode: 'string',
			},
		},
	},
}
export default config
