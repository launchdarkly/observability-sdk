import { GraphQLClient } from 'graphql-request'
import { GraphQLClientRequestHeaders } from 'graphql-request/build/cjs/types'
import gql from 'graphql-tag'
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
	Any: { input: any; output: any }
	Int64: { input: any; output: any }
	Timestamp: { input: any; output: any }
}

/** An attribute match configuration which can match an attribute key and value. */
export type AttributeMatchConfig = {
	__typename?: 'AttributeMatchConfig'
	attribute: MatchConfig
	key: MatchConfig
}

export type BackendErrorObjectInput = {
	environment: Scalars['String']['input']
	event: Scalars['String']['input']
	log_cursor?: InputMaybe<Scalars['String']['input']>
	payload?: InputMaybe<Scalars['String']['input']>
	request_id?: InputMaybe<Scalars['String']['input']>
	service: ServiceInput
	session_secure_id?: InputMaybe<Scalars['String']['input']>
	source: Scalars['String']['input']
	span_id?: InputMaybe<Scalars['String']['input']>
	stackTrace: Scalars['String']['input']
	timestamp: Scalars['Timestamp']['input']
	trace_id?: InputMaybe<Scalars['String']['input']>
	type: Scalars['String']['input']
	url: Scalars['String']['input']
}

export type ErrorObjectInput = {
	columnNumber: Scalars['Int']['input']
	event: Scalars['String']['input']
	lineNumber: Scalars['Int']['input']
	payload?: InputMaybe<Scalars['String']['input']>
	source: Scalars['String']['input']
	stackTrace: Array<InputMaybe<StackFrameInput>>
	timestamp: Scalars['Timestamp']['input']
	type: Scalars['String']['input']
	url: Scalars['String']['input']
}

export type InitializeSessionResponse = {
	__typename?: 'InitializeSessionResponse'
	project_id: Scalars['ID']['output']
	sampling?: Maybe<SamplingConfig>
	secure_id: Scalars['String']['output']
}

/**
 * A match based log sampling configuration. A log matches if each specified matching configuration matches.
 * If no matching configuration is specified, then all spans will match.
 * The sampling ratio will be applied to all matching spans.
 */
export type LogSamplingConfig = {
	__typename?: 'LogSamplingConfig'
	/**
	 * A list of attribute match configs.
	 * In order to match each attribute listed must match. This is an implicit AND operation.
	 */
	attributes?: Maybe<Array<AttributeMatchConfig>>
	/** Matches against the log message. */
	message?: Maybe<MatchConfig>
	/**
	 * The ratio of logs to sample. Expressed in the form 1/n. So if the ratio is 10, then 1 out of
	 * every 10 logs will be sampled. Setting the ratio to 0 will disable sampling for the log.
	 */
	samplingRatio: Scalars['Int']['output']
	/** Matches against the severity of the log. */
	severityText?: Maybe<MatchConfig>
}

/**
 * A match configuration. Each field of this type represents a different type of match
 * configuration. One and only 1 field should be populated.
 *
 * This is effectively a sum type/discriminated union, but isn't implemented as such to avoid
 * this bug: https://github.com/99designs/gqlgen/issues/2741
 */
export type MatchConfig = {
	__typename?: 'MatchConfig'
	/** A match configuration which does an exact match against any value. */
	matchValue?: Maybe<Scalars['Any']['output']>
	/**
	 * A match configuration which matches against a regular expression.
	 * Can only match string attributes.
	 */
	regexValue?: Maybe<Scalars['String']['output']>
}

export type MetricInput = {
	category?: InputMaybe<Scalars['String']['input']>
	group?: InputMaybe<Scalars['String']['input']>
	name: Scalars['String']['input']
	parent_span_id?: InputMaybe<Scalars['String']['input']>
	session_secure_id: Scalars['String']['input']
	span_id?: InputMaybe<Scalars['String']['input']>
	tags?: InputMaybe<Array<MetricTag>>
	timestamp: Scalars['Timestamp']['input']
	trace_id?: InputMaybe<Scalars['String']['input']>
	value: Scalars['Float']['input']
}

export type MetricTag = {
	name: Scalars['String']['input']
	value: Scalars['String']['input']
}

export type Mutation = {
	__typename?: 'Mutation'
	addSessionFeedback: Scalars['String']['output']
	addSessionProperties: Scalars['String']['output']
	identifySession: Scalars['String']['output']
	initializeSession: InitializeSessionResponse
	markBackendSetup?: Maybe<Scalars['Any']['output']>
	pushBackendPayload?: Maybe<Scalars['Any']['output']>
	pushMetrics: Scalars['Int']['output']
	pushPayload: Scalars['Int']['output']
	pushPayloadCompressed?: Maybe<Scalars['Any']['output']>
}

export type MutationAddSessionFeedbackArgs = {
	session_secure_id: Scalars['String']['input']
	timestamp: Scalars['Timestamp']['input']
	user_email?: InputMaybe<Scalars['String']['input']>
	user_name?: InputMaybe<Scalars['String']['input']>
	verbatim: Scalars['String']['input']
}

export type MutationAddSessionPropertiesArgs = {
	properties_object?: InputMaybe<Scalars['Any']['input']>
	session_secure_id: Scalars['String']['input']
}

export type MutationIdentifySessionArgs = {
	session_secure_id: Scalars['String']['input']
	user_identifier: Scalars['String']['input']
	user_object?: InputMaybe<Scalars['Any']['input']>
}

export type MutationInitializeSessionArgs = {
	appVersion?: InputMaybe<Scalars['String']['input']>
	clientConfig: Scalars['String']['input']
	clientVersion: Scalars['String']['input']
	client_id: Scalars['String']['input']
	disable_session_recording?: InputMaybe<Scalars['Boolean']['input']>
	enable_recording_network_contents: Scalars['Boolean']['input']
	enable_strict_privacy: Scalars['Boolean']['input']
	environment: Scalars['String']['input']
	fingerprint: Scalars['String']['input']
	firstloadVersion: Scalars['String']['input']
	network_recording_domains?: InputMaybe<Array<Scalars['String']['input']>>
	organization_verbose_id: Scalars['String']['input']
	privacy_setting?: InputMaybe<Scalars['String']['input']>
	serviceName?: InputMaybe<Scalars['String']['input']>
	session_secure_id: Scalars['String']['input']
}

export type MutationMarkBackendSetupArgs = {
	project_id?: InputMaybe<Scalars['String']['input']>
	session_secure_id?: InputMaybe<Scalars['String']['input']>
	type?: InputMaybe<Scalars['String']['input']>
}

export type MutationPushBackendPayloadArgs = {
	errors: Array<InputMaybe<BackendErrorObjectInput>>
	project_id?: InputMaybe<Scalars['String']['input']>
}

export type MutationPushMetricsArgs = {
	metrics: Array<InputMaybe<MetricInput>>
}

export type MutationPushPayloadArgs = {
	errors: Array<InputMaybe<ErrorObjectInput>>
	events: ReplayEventsInput
	has_session_unloaded?: InputMaybe<Scalars['Boolean']['input']>
	highlight_logs?: InputMaybe<Scalars['String']['input']>
	is_beacon?: InputMaybe<Scalars['Boolean']['input']>
	messages: Scalars['String']['input']
	payload_id?: InputMaybe<Scalars['ID']['input']>
	resources: Scalars['String']['input']
	session_secure_id: Scalars['String']['input']
	web_socket_events?: InputMaybe<Scalars['String']['input']>
}

export type MutationPushPayloadCompressedArgs = {
	data: Scalars['String']['input']
	payload_id: Scalars['ID']['input']
	session_secure_id: Scalars['String']['input']
}

export enum PublicGraphError {
	BillingQuotaExceeded = 'BillingQuotaExceeded',
}

export type Query = {
	__typename?: 'Query'
	ignore?: Maybe<Scalars['Any']['output']>
	sampling: SamplingConfig
}

export type QueryIgnoreArgs = {
	id: Scalars['ID']['input']
}

export type QuerySamplingArgs = {
	project_id: Scalars['ID']['input']
}

export type ReplayEventInput = {
	_sid: Scalars['Float']['input']
	data: Scalars['Any']['input']
	timestamp: Scalars['Float']['input']
	type: Scalars['Int']['input']
}

export type ReplayEventsInput = {
	events: Array<InputMaybe<ReplayEventInput>>
}

export type SamplingConfig = {
	__typename?: 'SamplingConfig'
	logs?: Maybe<Array<LogSamplingConfig>>
	spans?: Maybe<Array<SpanSamplingConfig>>
}

export type ServiceInput = {
	name: Scalars['String']['input']
	version: Scalars['String']['input']
}

export type Session = {
	__typename?: 'Session'
	id?: Maybe<Scalars['ID']['output']>
	organization_id: Scalars['ID']['output']
	project_id: Scalars['ID']['output']
	secure_id: Scalars['String']['output']
}

/** An event matcher configuration which matches span events within a span. */
export type SpanEventMatchConfig = {
	__typename?: 'SpanEventMatchConfig'
	attributes?: Maybe<Array<AttributeMatchConfig>>
	name?: Maybe<MatchConfig>
}

/**
 * A match based span sampling configuration. A span matches if each specified matching configuration
 * matches.
 * If no matching configuration is specified, then all spans will match.
 * The sampling ratio will be applied to all matching spans.
 */
export type SpanSamplingConfig = {
	__typename?: 'SpanSamplingConfig'
	/**
	 * A list of attribute match configs.
	 * In order to match each attribute listed must match. This is an implicit AND operation.
	 */
	attributes?: Maybe<Array<AttributeMatchConfig>>
	/** A list of span event match configs. */
	events?: Maybe<Array<SpanEventMatchConfig>>
	name?: Maybe<MatchConfig>
	/**
	 * The ratio of spans to sample. Expressed in the form 1/n. So if the ratio is 10, then 1 out of
	 * every 10 spans will be sampled. Setting the ratio to 0 will disable sampling for the span.
	 */
	samplingRatio: Scalars['Int']['output']
}

export type StackFrameInput = {
	args?: InputMaybe<Array<InputMaybe<Scalars['Any']['input']>>>
	columnNumber?: InputMaybe<Scalars['Int']['input']>
	fileName?: InputMaybe<Scalars['String']['input']>
	functionName?: InputMaybe<Scalars['String']['input']>
	isEval?: InputMaybe<Scalars['Boolean']['input']>
	isNative?: InputMaybe<Scalars['Boolean']['input']>
	lineNumber?: InputMaybe<Scalars['Int']['input']>
	source?: InputMaybe<Scalars['String']['input']>
}

export type PushPayloadMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	payload_id: Scalars['ID']['input']
	events: ReplayEventsInput
	messages: Scalars['String']['input']
	resources: Scalars['String']['input']
	web_socket_events: Scalars['String']['input']
	errors: Array<InputMaybe<ErrorObjectInput>> | InputMaybe<ErrorObjectInput>
	is_beacon?: InputMaybe<Scalars['Boolean']['input']>
	has_session_unloaded?: InputMaybe<Scalars['Boolean']['input']>
	highlight_logs?: InputMaybe<Scalars['String']['input']>
}>

export type PushPayloadMutation = {
	__typename?: 'Mutation'
	pushPayload: number
}

export type PushPayloadCompressedMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	payload_id: Scalars['ID']['input']
	data: Scalars['String']['input']
}>

export type PushPayloadCompressedMutation = {
	__typename?: 'Mutation'
	pushPayloadCompressed?: any | null
}

export type IdentifySessionMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	user_identifier: Scalars['String']['input']
	user_object?: InputMaybe<Scalars['Any']['input']>
}>

export type IdentifySessionMutation = {
	__typename?: 'Mutation'
	identifySession: string
}

export type AddSessionPropertiesMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	properties_object?: InputMaybe<Scalars['Any']['input']>
}>

export type AddSessionPropertiesMutation = {
	__typename?: 'Mutation'
	addSessionProperties: string
}

export type PushMetricsMutationVariables = Exact<{
	metrics: Array<InputMaybe<MetricInput>> | InputMaybe<MetricInput>
}>

export type PushMetricsMutation = {
	__typename?: 'Mutation'
	pushMetrics: number
}

export type AddSessionFeedbackMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	user_name?: InputMaybe<Scalars['String']['input']>
	user_email?: InputMaybe<Scalars['String']['input']>
	verbatim: Scalars['String']['input']
	timestamp: Scalars['Timestamp']['input']
}>

export type AddSessionFeedbackMutation = {
	__typename?: 'Mutation'
	addSessionFeedback: string
}

export type MatchPartsFragment = {
	__typename?: 'MatchConfig'
	regexValue?: string | null
}

export type InitializeSessionMutationVariables = Exact<{
	session_secure_id: Scalars['String']['input']
	organization_verbose_id: Scalars['String']['input']
	enable_strict_privacy: Scalars['Boolean']['input']
	privacy_setting: Scalars['String']['input']
	enable_recording_network_contents: Scalars['Boolean']['input']
	clientVersion: Scalars['String']['input']
	firstloadVersion: Scalars['String']['input']
	clientConfig: Scalars['String']['input']
	environment: Scalars['String']['input']
	id: Scalars['String']['input']
	appVersion?: InputMaybe<Scalars['String']['input']>
	serviceName: Scalars['String']['input']
	client_id: Scalars['String']['input']
	network_recording_domains?: InputMaybe<
		Array<Scalars['String']['input']> | Scalars['String']['input']
	>
	disable_session_recording?: InputMaybe<Scalars['Boolean']['input']>
}>

export type InitializeSessionMutation = {
	__typename?: 'Mutation'
	initializeSession: {
		__typename?: 'InitializeSessionResponse'
		secure_id: string
		project_id: string
		sampling?: {
			__typename?: 'SamplingConfig'
			spans?: Array<{
				__typename?: 'SpanSamplingConfig'
				samplingRatio: number
				name?: {
					__typename?: 'MatchConfig'
					regexValue?: string | null
				} | null
				attributes?: Array<{
					__typename?: 'AttributeMatchConfig'
					key: {
						__typename?: 'MatchConfig'
						regexValue?: string | null
					}
					attribute: {
						__typename?: 'MatchConfig'
						regexValue?: string | null
					}
				}> | null
			}> | null
			logs?: Array<{
				__typename?: 'LogSamplingConfig'
				samplingRatio: number
				message?: {
					__typename?: 'MatchConfig'
					regexValue?: string | null
				} | null
				severityText?: {
					__typename?: 'MatchConfig'
					regexValue?: string | null
				} | null
				attributes?: Array<{
					__typename?: 'AttributeMatchConfig'
					key: {
						__typename?: 'MatchConfig'
						regexValue?: string | null
					}
					attribute: {
						__typename?: 'MatchConfig'
						regexValue?: string | null
					}
				}> | null
			}> | null
		} | null
	}
}

export type IgnoreQueryVariables = Exact<{
	id: Scalars['ID']['input']
}>

export type IgnoreQuery = { __typename?: 'Query'; ignore?: any | null }

export type GetSamplingConfigQueryVariables = Exact<{
	project_id: Scalars['ID']['input']
}>

export type GetSamplingConfigQuery = {
	__typename?: 'Query'
	sampling: {
		__typename?: 'SamplingConfig'
		spans?: Array<{
			__typename?: 'SpanSamplingConfig'
			samplingRatio: number
			name?: {
				__typename?: 'MatchConfig'
				regexValue?: string | null
			} | null
			attributes?: Array<{
				__typename?: 'AttributeMatchConfig'
				key: { __typename?: 'MatchConfig'; regexValue?: string | null }
				attribute: {
					__typename?: 'MatchConfig'
					regexValue?: string | null
				}
			}> | null
		}> | null
		logs?: Array<{
			__typename?: 'LogSamplingConfig'
			samplingRatio: number
			message?: {
				__typename?: 'MatchConfig'
				regexValue?: string | null
			} | null
			severityText?: {
				__typename?: 'MatchConfig'
				regexValue?: string | null
			} | null
			attributes?: Array<{
				__typename?: 'AttributeMatchConfig'
				key: { __typename?: 'MatchConfig'; regexValue?: string | null }
				attribute: {
					__typename?: 'MatchConfig'
					regexValue?: string | null
				}
			}> | null
		}> | null
	}
}

export const MatchPartsFragmentDoc = gql`
	fragment MatchParts on MatchConfig {
		regexValue
	}
`
export const PushPayloadDocument = gql`
	mutation PushPayload(
		$session_secure_id: String!
		$payload_id: ID!
		$events: ReplayEventsInput!
		$messages: String!
		$resources: String!
		$web_socket_events: String!
		$errors: [ErrorObjectInput]!
		$is_beacon: Boolean
		$has_session_unloaded: Boolean
		$highlight_logs: String
	) {
		pushPayload(
			session_secure_id: $session_secure_id
			payload_id: $payload_id
			events: $events
			messages: $messages
			resources: $resources
			web_socket_events: $web_socket_events
			errors: $errors
			is_beacon: $is_beacon
			has_session_unloaded: $has_session_unloaded
			highlight_logs: $highlight_logs
		)
	}
`
export const PushPayloadCompressedDocument = gql`
	mutation PushPayloadCompressed(
		$session_secure_id: String!
		$payload_id: ID!
		$data: String!
	) {
		pushPayloadCompressed(
			session_secure_id: $session_secure_id
			payload_id: $payload_id
			data: $data
		)
	}
`
export const IdentifySessionDocument = gql`
	mutation identifySession(
		$session_secure_id: String!
		$user_identifier: String!
		$user_object: Any
	) {
		identifySession(
			session_secure_id: $session_secure_id
			user_identifier: $user_identifier
			user_object: $user_object
		)
	}
`
export const AddSessionPropertiesDocument = gql`
	mutation addSessionProperties(
		$session_secure_id: String!
		$properties_object: Any
	) {
		addSessionProperties(
			session_secure_id: $session_secure_id
			properties_object: $properties_object
		)
	}
`
export const PushMetricsDocument = gql`
	mutation pushMetrics($metrics: [MetricInput]!) {
		pushMetrics(metrics: $metrics)
	}
`
export const AddSessionFeedbackDocument = gql`
	mutation addSessionFeedback(
		$session_secure_id: String!
		$user_name: String
		$user_email: String
		$verbatim: String!
		$timestamp: Timestamp!
	) {
		addSessionFeedback(
			session_secure_id: $session_secure_id
			user_name: $user_name
			user_email: $user_email
			verbatim: $verbatim
			timestamp: $timestamp
		)
	}
`
export const InitializeSessionDocument = gql`
	mutation initializeSession(
		$session_secure_id: String!
		$organization_verbose_id: String!
		$enable_strict_privacy: Boolean!
		$privacy_setting: String!
		$enable_recording_network_contents: Boolean!
		$clientVersion: String!
		$firstloadVersion: String!
		$clientConfig: String!
		$environment: String!
		$id: String!
		$appVersion: String
		$serviceName: String!
		$client_id: String!
		$network_recording_domains: [String!]
		$disable_session_recording: Boolean
	) {
		initializeSession(
			session_secure_id: $session_secure_id
			organization_verbose_id: $organization_verbose_id
			enable_strict_privacy: $enable_strict_privacy
			enable_recording_network_contents: $enable_recording_network_contents
			clientVersion: $clientVersion
			firstloadVersion: $firstloadVersion
			clientConfig: $clientConfig
			environment: $environment
			appVersion: $appVersion
			serviceName: $serviceName
			fingerprint: $id
			client_id: $client_id
			network_recording_domains: $network_recording_domains
			disable_session_recording: $disable_session_recording
			privacy_setting: $privacy_setting
		) {
			secure_id
			project_id
			sampling {
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
	}
	${MatchPartsFragmentDoc}
`
export const IgnoreDocument = gql`
	query Ignore($id: ID!) {
		ignore(id: $id)
	}
`
export const GetSamplingConfigDocument = gql`
	query GetSamplingConfig($project_id: ID!) {
		sampling(project_id: $project_id) {
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
	${MatchPartsFragmentDoc}
`

export type SdkFunctionWrapper = <T>(
	action: (requestHeaders?: Record<string, string>) => Promise<T>,
	operationName: string,
	operationType?: string,
	variables?: any,
) => Promise<T>

const defaultWrapper: SdkFunctionWrapper = (
	action,
	_operationName,
	_operationType,
	variables,
) => action()

export function getSdk(
	client: GraphQLClient,
	withWrapper: SdkFunctionWrapper = defaultWrapper,
) {
	return {
		PushPayload(
			variables: PushPayloadMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<PushPayloadMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<PushPayloadMutation>(
						PushPayloadDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'PushPayload',
				'mutation',
				variables,
			)
		},
		PushPayloadCompressed(
			variables: PushPayloadCompressedMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<PushPayloadCompressedMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<PushPayloadCompressedMutation>(
						PushPayloadCompressedDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'PushPayloadCompressed',
				'mutation',
				variables,
			)
		},
		identifySession(
			variables: IdentifySessionMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<IdentifySessionMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<IdentifySessionMutation>(
						IdentifySessionDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'identifySession',
				'mutation',
				variables,
			)
		},
		addSessionProperties(
			variables: AddSessionPropertiesMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<AddSessionPropertiesMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<AddSessionPropertiesMutation>(
						AddSessionPropertiesDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'addSessionProperties',
				'mutation',
				variables,
			)
		},
		pushMetrics(
			variables: PushMetricsMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<PushMetricsMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<PushMetricsMutation>(
						PushMetricsDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'pushMetrics',
				'mutation',
				variables,
			)
		},
		addSessionFeedback(
			variables: AddSessionFeedbackMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<AddSessionFeedbackMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<AddSessionFeedbackMutation>(
						AddSessionFeedbackDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'addSessionFeedback',
				'mutation',
				variables,
			)
		},
		initializeSession(
			variables: InitializeSessionMutationVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<InitializeSessionMutation> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<InitializeSessionMutation>(
						InitializeSessionDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'initializeSession',
				'mutation',
				variables,
			)
		},
		Ignore(
			variables: IgnoreQueryVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<IgnoreQuery> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<IgnoreQuery>(IgnoreDocument, variables, {
						...requestHeaders,
						...wrappedRequestHeaders,
					}),
				'Ignore',
				'query',
				variables,
			)
		},
		GetSamplingConfig(
			variables: GetSamplingConfigQueryVariables,
			requestHeaders?: GraphQLClientRequestHeaders,
		): Promise<GetSamplingConfigQuery> {
			return withWrapper(
				(wrappedRequestHeaders) =>
					client.request<GetSamplingConfigQuery>(
						GetSamplingConfigDocument,
						variables,
						{ ...requestHeaders, ...wrappedRequestHeaders },
					),
				'GetSamplingConfig',
				'query',
				variables,
			)
		},
	}
}
export type Sdk = ReturnType<typeof getSdk>
