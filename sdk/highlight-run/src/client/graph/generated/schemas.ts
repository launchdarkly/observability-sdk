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
	organization_verbose_id: Scalars['String']['input']
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
