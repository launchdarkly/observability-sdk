import { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core'
export type Maybe<T> = T | null
export type InputMaybe<T> = Maybe<T>
export type Exact<T extends { [key: string]: unknown }> = {
	[K in keyof T]: T[K]
}
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & {
	[SubKey in K]?: Maybe<T[SubKey]>
}
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & {
	[SubKey in K]: Maybe<T[SubKey]>
}
export type MakeEmpty<
	T extends { [key: string]: unknown },
	K extends keyof T,
> = { [_ in K]?: never }
export type Incremental<T> =
	| T
	| {
			[P in keyof T]?: P extends ' $fragmentName' | '__typename'
				? T[P]
				: never
	  }
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
	ID: { input: string; output: string }
	String: { input: string; output: string }
	Boolean: { input: boolean; output: boolean }
	Int: { input: number; output: number }
	Float: { input: number; output: number }
	/** A date-time string at UTC, such as 2007-12-03T10:15:30Z, compliant with the `date-time` format outlined in section 5.6 of the RFC 3339 profile of the ISO 8601 standard for representation of dates and times using the Gregorian calendar. */
	DateTime: { input: any; output: any }
	/** The `JSON` scalar type represents JSON values as specified by [ECMA-404](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf). */
	JSON: { input: any; output: any }
	/** The `JSONObject` scalar type represents JSON objects as specified by [ECMA-404](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf). */
	JSONObject: { input: any; output: any }
	/** A field whose value conforms to the standard URL format as specified in RFC3986: https://www.ietf.org/rfc/rfc3986.txt. */
	URL: { input: any; output: any }
}

export type AttributeMatchConfig = {
	__typename?: 'AttributeMatchConfig'
	attribute?: Maybe<MatchConfig>
	key?: Maybe<MatchConfig>
}

export type LogSamplingConfig = {
	__typename?: 'LogSamplingConfig'
	attributes?: Maybe<Array<AttributeMatchConfig>>
	message?: Maybe<MatchConfig>
	samplingRatio: Scalars['Float']['output']
	severityText?: Maybe<MatchConfig>
}

export type MatchConfig = {
	__typename?: 'MatchConfig'
	matchValue?: Maybe<Scalars['String']['output']>
	regexValue?: Maybe<Scalars['String']['output']>
}

export type Query = {
	__typename?: 'Query'
	sampling_config?: Maybe<SamplingConfig>
}

export type QuerySampling_ConfigArgs = {
	project_id: Scalars['String']['input']
}

export type SamplingConfig = {
	__typename?: 'SamplingConfig'
	logs?: Maybe<Array<LogSamplingConfig>>
	spans?: Maybe<Array<SpanSamplingConfig>>
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
	samplingRatio: Scalars['Float']['output']
}

export type GetSamplingConfigQueryVariables = Exact<{
	project_id: Scalars['String']['input']
}>

export type GetSamplingConfigQuery = {
	__typename?: 'Query'
	sampling_config?: {
		__typename?: 'SamplingConfig'
		logs?: Array<{
			__typename?: 'LogSamplingConfig'
			samplingRatio: number
			severityText?: {
				__typename?: 'MatchConfig'
				matchValue?: string | null
				regexValue?: string | null
			} | null
			message?: {
				__typename?: 'MatchConfig'
				matchValue?: string | null
				regexValue?: string | null
			} | null
			attributes?: Array<{
				__typename?: 'AttributeMatchConfig'
				key?: {
					__typename?: 'MatchConfig'
					matchValue?: string | null
					regexValue?: string | null
				} | null
				attribute?: {
					__typename?: 'MatchConfig'
					matchValue?: string | null
					regexValue?: string | null
				} | null
			}> | null
		}> | null
		spans?: Array<{
			__typename?: 'SpanSamplingConfig'
			samplingRatio: number
			name?: {
				__typename?: 'MatchConfig'
				matchValue?: string | null
				regexValue?: string | null
			} | null
			attributes?: Array<{
				__typename?: 'AttributeMatchConfig'
				key?: {
					__typename?: 'MatchConfig'
					matchValue?: string | null
					regexValue?: string | null
				} | null
				attribute?: {
					__typename?: 'MatchConfig'
					matchValue?: string | null
					regexValue?: string | null
				} | null
			}> | null
			events?: Array<{
				__typename?: 'SpanEventMatchConfig'
				name?: {
					__typename?: 'MatchConfig'
					matchValue?: string | null
					regexValue?: string | null
				} | null
				attributes?: Array<{
					__typename?: 'AttributeMatchConfig'
					key?: {
						__typename?: 'MatchConfig'
						matchValue?: string | null
						regexValue?: string | null
					} | null
					attribute?: {
						__typename?: 'MatchConfig'
						matchValue?: string | null
						regexValue?: string | null
					} | null
				}> | null
			}> | null
		}> | null
	} | null
}

export const GetSamplingConfigDocument = {
	kind: 'Document',
	definitions: [
		{
			kind: 'OperationDefinition',
			operation: 'query',
			name: { kind: 'Name', value: 'GetSamplingConfig' },
			variableDefinitions: [
				{
					kind: 'VariableDefinition',
					variable: {
						kind: 'Variable',
						name: { kind: 'Name', value: 'project_id' },
					},
					type: {
						kind: 'NonNullType',
						type: {
							kind: 'NamedType',
							name: { kind: 'Name', value: 'String' },
						},
					},
				},
			],
			selectionSet: {
				kind: 'SelectionSet',
				selections: [
					{
						kind: 'Field',
						name: { kind: 'Name', value: 'sampling_config' },
						arguments: [
							{
								kind: 'Argument',
								name: { kind: 'Name', value: 'project_id' },
								value: {
									kind: 'Variable',
									name: { kind: 'Name', value: 'project_id' },
								},
							},
						],
						selectionSet: {
							kind: 'SelectionSet',
							selections: [
								{
									kind: 'Field',
									name: { kind: 'Name', value: 'logs' },
									selectionSet: {
										kind: 'SelectionSet',
										selections: [
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'samplingRatio',
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'severityText',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'matchValue',
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'regexValue',
															},
														},
													],
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'message',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'matchValue',
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'regexValue',
															},
														},
													],
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'attributes',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'key',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'matchValue',
																		},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'regexValue',
																		},
																	},
																],
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'attribute',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'matchValue',
																		},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'regexValue',
																		},
																	},
																],
															},
														},
													],
												},
											},
										],
									},
								},
								{
									kind: 'Field',
									name: { kind: 'Name', value: 'spans' },
									selectionSet: {
										kind: 'SelectionSet',
										selections: [
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'samplingRatio',
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'name',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'matchValue',
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'regexValue',
															},
														},
													],
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'attributes',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'key',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'matchValue',
																		},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'regexValue',
																		},
																	},
																],
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'attribute',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'matchValue',
																		},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'regexValue',
																		},
																	},
																],
															},
														},
													],
												},
											},
											{
												kind: 'Field',
												name: {
													kind: 'Name',
													value: 'events',
												},
												selectionSet: {
													kind: 'SelectionSet',
													selections: [
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'name',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'matchValue',
																		},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'regexValue',
																		},
																	},
																],
															},
														},
														{
															kind: 'Field',
															name: {
																kind: 'Name',
																value: 'attributes',
															},
															selectionSet: {
																kind: 'SelectionSet',
																selections: [
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'key',
																		},
																		selectionSet:
																			{
																				kind: 'SelectionSet',
																				selections:
																					[
																						{
																							kind: 'Field',
																							name: {
																								kind: 'Name',
																								value: 'matchValue',
																							},
																						},
																						{
																							kind: 'Field',
																							name: {
																								kind: 'Name',
																								value: 'regexValue',
																							},
																						},
																					],
																			},
																	},
																	{
																		kind: 'Field',
																		name: {
																			kind: 'Name',
																			value: 'attribute',
																		},
																		selectionSet:
																			{
																				kind: 'SelectionSet',
																				selections:
																					[
																						{
																							kind: 'Field',
																							name: {
																								kind: 'Name',
																								value: 'matchValue',
																							},
																						},
																						{
																							kind: 'Field',
																							name: {
																								kind: 'Name',
																								value: 'regexValue',
																							},
																						},
																					],
																			},
																	},
																],
															},
														},
													],
												},
											},
										],
									},
								},
							],
						},
					},
				],
			},
		},
	],
} as unknown as DocumentNode<
	GetSamplingConfigQuery,
	GetSamplingConfigQueryVariables
>
