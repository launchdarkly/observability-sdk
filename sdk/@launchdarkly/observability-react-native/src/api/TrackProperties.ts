/**
 * A plain, loosely-typed property value accepted by {@link Observe.track}.
 *
 * This mirrors the `[String: Any]` (iOS) / `Map<String, Any?>` (Android) `track`
 * surface so callers can pass ordinary dictionaries — including nested objects
 * and arrays — without first reshaping them into flat OpenTelemetry attributes.
 * The SDK flattens the structure into attributes before recording the span.
 */
export type TrackPropertyValue =
	| string
	| number
	| boolean
	| null
	| undefined
	| TrackPropertyValue[]
	| { [key: string]: TrackPropertyValue }

/**
 * A plain dictionary of {@link Observe.track} properties.
 */
export type TrackProperties = { [key: string]: TrackPropertyValue }
