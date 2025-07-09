/* eslint-disable */
import * as types from './graphql'
import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core'

/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance advantages:
 * 1. It allows us to reference operations by name instead of by their full document
 * 2. It enables us to avoid parsing the same document multiple times
 * 3. It provides better tree-shaking since unused operations can be removed
 */
const documents = {
	'\n\tquery GetSamplingConfig($project_id: String!) {\n\t\tsampling_config(project_id: $project_id) {\n\t\t\tlogs {\n\t\t\t\tsamplingRatio\n\t\t\t\tseverityText {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tmessage {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t\tspans {\n\t\t\t\tsamplingRatio\n\t\t\t\tname {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t\tevents {\n\t\t\t\t\tname {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattributes {\n\t\t\t\t\t\tkey {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t\tattribute {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n':
		types.GetSamplingConfigDocument,
}

/**
 * The gql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 *
 *
 * @example
 * ```ts
 * const query = gql(`query GetUser($id: ID!) { user(id: $id) { name } }`);
 * ```
 *
 * The query argument is unknown!
 * Please regenerate the types.
 */
export function gql(source: string): unknown

/**
 * The gql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function gql(
	source: '\n\tquery GetSamplingConfig($project_id: String!) {\n\t\tsampling_config(project_id: $project_id) {\n\t\t\tlogs {\n\t\t\t\tsamplingRatio\n\t\t\t\tseverityText {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tmessage {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t\tspans {\n\t\t\t\tsamplingRatio\n\t\t\t\tname {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t\tevents {\n\t\t\t\t\tname {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattributes {\n\t\t\t\t\t\tkey {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t\tattribute {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n',
): (typeof documents)['\n\tquery GetSamplingConfig($project_id: String!) {\n\t\tsampling_config(project_id: $project_id) {\n\t\t\tlogs {\n\t\t\t\tsamplingRatio\n\t\t\t\tseverityText {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tmessage {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t\tspans {\n\t\t\t\tsamplingRatio\n\t\t\t\tname {\n\t\t\t\t\tmatchValue\n\t\t\t\t\tregexValue\n\t\t\t\t}\n\t\t\t\tattributes {\n\t\t\t\t\tkey {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattribute {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t\tevents {\n\t\t\t\t\tname {\n\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\tregexValue\n\t\t\t\t\t}\n\t\t\t\t\tattributes {\n\t\t\t\t\t\tkey {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t\tattribute {\n\t\t\t\t\t\t\tmatchValue\n\t\t\t\t\t\t\tregexValue\n\t\t\t\t\t\t}\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}\n\t\t}\n\t}\n']

export function gql(source: string) {
	return (documents as any)[source] ?? {}
}

export type DocumentType<TDocumentNode extends DocumentNode<any, any>> =
	TDocumentNode extends DocumentNode<infer TType, any> ? TType : never
