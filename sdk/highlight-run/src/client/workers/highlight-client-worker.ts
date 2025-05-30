import { compressSync, strToU8 } from 'fflate'
import { GraphQLClient } from 'graphql-request'
import stringify from 'json-stringify-safe'
import { UPLOAD_TIMEOUT } from '../constants/sessions'
import {
	getSdk,
	PushPayloadMutationVariables,
	Sdk,
} from '../graph/generated/operations'
import { ReplayEventsInput } from '../graph/generated/schemas'
import { Logger } from '../logger'
import { MetricCategory } from '../types/client'
import { getGraphQLRequestWrapper } from '../utils/graph'
import {
	MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS,
	NON_SERIALIZABLE_PROPS,
	PROPERTY_MAX_LENGTH,
} from './constants'
import {
	AsyncEventsMessage,
	AsyncEventsResponse,
	FeedbackMessage,
	HighlightClientWorkerParams,
	HighlightClientWorkerResponse,
	IdentifyMessage,
	MessageType,
	MetricsMessage,
	PropertiesMessage,
} from './types'

export interface HighlightClientRequestWorker {
	postMessage: (message: HighlightClientWorkerParams) => void
	onmessage: (message: MessageEvent<HighlightClientWorkerResponse>) => void
}

interface HighlightClientResponseWorker {
	onmessage:
		| null
		| ((message: MessageEvent<HighlightClientWorkerParams>) => void)

	postMessage(e: HighlightClientWorkerResponse): void
}

async function bufferToBase64(buffer: Uint8Array) {
	// use a FileReader to generate a base64 data URI:
	const base64url = await new Promise<string>((r) => {
		const reader = new FileReader()
		reader.onload = () => r(reader.result as string)
		reader.readAsDataURL(new Blob([buffer]))
	})
	// remove data:application/octet-stream;base64, prefix
	return base64url.slice(base64url.indexOf(',') + 1)
}

// `as any` because: https://github.com/Microsoft/TypeScript/issues/20595
const worker: HighlightClientResponseWorker = self as any

function stringifyProperties(
	properties_object: any,
	type: 'session' | 'track' | 'user',
) {
	const stringifiedObj: any = {}
	const invalidTypes: any[] = []
	const tooLong: any[] = []
	for (const [key, value] of Object.entries(properties_object)) {
		if (value === undefined || value === null) {
			continue
		}

		if (!NON_SERIALIZABLE_PROPS.includes(typeof value)) {
			invalidTypes.push({ [key]: value })
		}
		let asString: string
		if (typeof value === 'string') {
			asString = value
		} else {
			asString = stringify(value)
		}
		if (asString.length > PROPERTY_MAX_LENGTH) {
			tooLong.push({ [key]: value })
			asString = asString.substring(0, PROPERTY_MAX_LENGTH)
		}

		stringifiedObj[key] = asString
	}

	// Skipping logging for 'session' type because they're generated by Highlight
	// (e.g. visited-url > 2000 characters)
	if (type !== 'session') {
		if (invalidTypes.length > 0) {
			console.warn(
				`Highlight was passed one or more ${type} properties not of type string, number, or boolean.`,
				invalidTypes,
			)
		}

		if (tooLong.length > 0) {
			console.warn(
				`Highlight was passed one or more ${type} properties exceeding 2000 characters, which will be truncated.`,
				tooLong,
			)
		}
	}

	return stringifiedObj
}

{
	let graphqlSDK: Sdk
	let backend: string
	let sessionSecureID: string
	let numberOfFailedRequests: number = 0
	let numberOfFailedPushPayloads: number = 0
	let debug: boolean = false
	let recordingStartTime: number = 0
	let logger = new Logger(false, '[worker]')
	const metricsPayload: {
		name: string
		value: number
		session_secure_id: string
		category: MetricCategory
		group: string
		timestamp: string
		tags: { name: string; value: string }[]
	}[] = []

	const shouldSendRequest = (): boolean => {
		return (
			recordingStartTime !== 0 &&
			numberOfFailedRequests < MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS &&
			!!sessionSecureID?.length
		)
	}

	const addCustomEvent = <T>(tag: string, payload: T) => {
		worker.postMessage({
			response: {
				type: MessageType.CustomEvent,
				tag: tag,
				payload: payload,
			},
		})
	}

	const processAsyncEventsMessage = async (msg: AsyncEventsMessage) => {
		const {
			id,
			events,
			messages,
			errors,
			resourcesString,
			webSocketEventsString,
			hasSessionUnloaded,
			highlightLogs,
		} = msg

		const messagesString = stringify({ messages: messages })
		let payload: PushPayloadMutationVariables = {
			session_secure_id: sessionSecureID,
			payload_id: id.toString(),
			events: { events } as ReplayEventsInput,
			messages: messagesString,
			resources: resourcesString,
			web_socket_events: webSocketEventsString,
			errors,
			is_beacon: false,
			has_session_unloaded: hasSessionUnloaded,
		}
		if (highlightLogs) {
			payload.highlight_logs = highlightLogs
		}

		const buf = strToU8(JSON.stringify(payload))
		const compressed = compressSync(buf)
		const compressedBase64 = await bufferToBase64(compressed)

		const response: AsyncEventsResponse = {
			type: MessageType.AsyncEvents,
			id,
			eventsSize: buf.length,
			compressedSize: compressedBase64.length,
		}

		logger.log(
			`Pushing payload: ${JSON.stringify(
				{
					sessionSecureID,
					id,
					firstSID: Math.min(
						...(payload.events.events
							.map((e) => e?._sid)
							.filter((sid) => !!sid) as number[]),
					),
					eventsLength: payload.events.events.length,
					messagesLength: messages.length,
					resourcesLength: resourcesString.length,
					webSocketLength: webSocketEventsString.length,
					errorsLength: errors.length,
					bufLength: buf.length,
					compressedLength: compressed.length,
					compressedBase64Length: compressedBase64.length,
				},
				undefined,
				2,
			)}`,
		)

		const pushPayload = graphqlSDK.PushPayloadCompressed({
			session_secure_id: sessionSecureID,
			payload_id: id.toString(),
			data: compressedBase64,
		})

		let pushMetrics: Promise<any> = Promise.resolve()
		if (metricsPayload.length) {
			pushMetrics = graphqlSDK.pushMetrics({
				metrics: metricsPayload,
			})
			// clear batched payload before yielding for network request
			metricsPayload.splice(0)
		}

		let requestStart: number = performance.now()
		const int = setInterval(() => {
			if (
				requestStart &&
				performance.now() - requestStart > UPLOAD_TIMEOUT
			) {
				console.warn(
					`Uploading pushPayload took too long, failure number #${numberOfFailedPushPayloads}.`,
				)
				numberOfFailedPushPayloads += 1
				clearInterval(int)

				if (
					numberOfFailedPushPayloads >=
					MAX_PUBLIC_GRAPH_RETRY_ATTEMPTS
				) {
					console.warn(
						`Uploading pushPayload took too long, stopping recording to avoid OOM.`,
					)

					worker.postMessage({
						response: {
							type: MessageType.Stop,
							requestStart,
							asyncEventsResponse: response,
						},
					})

					processPropertiesMessage({
						type: MessageType.Properties,
						propertiesObject: {
							stopReason: 'Push Payload Timeout',
						},
						propertyType: { type: 'track' },
					})
				}
			}
		}, 100)
		try {
			await Promise.all([pushPayload, pushMetrics])
			if (
				numberOfFailedPushPayloads &&
				performance.now() - requestStart <= UPLOAD_TIMEOUT
			) {
				console.warn(
					`pushPayload succeeded after #${numberOfFailedPushPayloads} failures, resetting stop switch.`,
				)
				numberOfFailedPushPayloads = 0
			}
		} finally {
			requestStart = 0
			clearInterval(int)
		}

		worker.postMessage({
			response,
		})
	}

	const processIdentifyMessage = async (msg: IdentifyMessage) => {
		const { userObject, userIdentifier, source } = msg
		if (source === 'segment') {
			addCustomEvent(
				'Segment Identify',
				stringify({ userIdentifier, ...userObject }),
			)
		} else {
			addCustomEvent(
				'Identify',
				stringify({ userIdentifier, ...userObject }),
			)
		}
		await graphqlSDK.identifySession({
			session_secure_id: sessionSecureID,
			user_identifier: userIdentifier,
			user_object: stringifyProperties(userObject, 'user'),
		})
		const sourceString = source === 'segment' ? source : 'default'
		logger.log(
			`Identify (${userIdentifier}, source: ${sourceString}) w/ obj: ${stringify(
				userObject,
			)} @ ${backend}`,
		)
	}

	const processPropertiesMessage = async (msg: PropertiesMessage) => {
		const { propertiesObject, propertyType } = msg
		let eventType: string
		if (propertyType?.type === 'session') {
			eventType = 'Session'
			// Session properties are custom properties that the Highlight snippet adds (visited-url, referrer, etc.)
			// These should be searchable but not part of `Track` timeline indicators
			await graphqlSDK.addSessionProperties({
				session_secure_id: sessionSecureID,
				properties_object: stringifyProperties(
					propertiesObject,
					'session',
				),
			})
		} else {
			// Track properties are properties that users define; rn, either through segment or manually.
			if (propertyType?.source === 'segment') {
				eventType = 'Segment'
			} else {
				eventType = 'Track'
			}
		}
		if (eventType !== 'Session') {
			addCustomEvent<string>(eventType, stringify(propertiesObject))
		}
		logger.log(
			`Adding ${eventType} Properties to session (${sessionSecureID}) w/ obj: ${JSON.stringify(
				propertiesObject,
			)} @ ${backend}`,
		)
	}

	const processMetricsMessage = async (msg: MetricsMessage) => {
		metricsPayload.push(
			...msg.metrics.map((m) => ({
				name: m.name,
				value: m.value,
				session_secure_id: sessionSecureID,
				category: m.category,
				group: m.group,
				timestamp: m.timestamp.toISOString(),
				tags: m.tags,
			})),
		)
	}

	const processFeedbackMessage = async (msg: FeedbackMessage) => {
		const { timestamp, verbatim, userEmail, userName } = msg
		await graphqlSDK.addSessionFeedback({
			session_secure_id: sessionSecureID,
			timestamp,
			verbatim,
			user_email: userEmail,
			user_name: userName,
		})
	}

	worker.onmessage = async function (e) {
		if (e.data.message.type === MessageType.Initialize) {
			backend = e.data.message.backend
			sessionSecureID = e.data.message.sessionSecureID
			debug = e.data.message.debug
			recordingStartTime = e.data.message.recordingStartTime
			logger.debug = debug
			graphqlSDK = getSdk(
				new GraphQLClient(backend, {
					headers: {},
				}),
				getGraphQLRequestWrapper(),
			)
			return
		}
		if (!shouldSendRequest()) {
			return
		}
		try {
			if (e.data.message.type === MessageType.AsyncEvents) {
				await processAsyncEventsMessage(
					e.data.message as AsyncEventsMessage,
				)
			} else if (e.data.message.type === MessageType.Identify) {
				await processIdentifyMessage(e.data.message as IdentifyMessage)
			} else if (e.data.message.type === MessageType.Properties) {
				await processPropertiesMessage(
					e.data.message as PropertiesMessage,
				)
			} else if (e.data.message.type === MessageType.Metrics) {
				await processMetricsMessage(e.data.message as MetricsMessage)
			} else if (e.data.message.type === MessageType.Feedback) {
				await processFeedbackMessage(e.data.message as FeedbackMessage)
			}
			numberOfFailedRequests = 0
		} catch (e) {
			if (debug) {
				console.error(e)
			}
			numberOfFailedRequests += 1
		}
	}
}
