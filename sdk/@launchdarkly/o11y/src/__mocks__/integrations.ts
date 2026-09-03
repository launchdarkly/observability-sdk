// Mock implementations for integrations

// Amplitude integration
export const createAmplitudeIntegration = () => {
	let highlightMethods: any = null
	let originalLogEvent: Function | null = null
	let originalSetUserId: Function | null = null
	let originalSetUserProperties: Function | null = null

	return {
		name: 'Amplitude',
		register: (amplitudeClient: any) => {
			if (!amplitudeClient) return

			// Store original methods
			originalLogEvent = amplitudeClient.logEvent
			originalSetUserId = amplitudeClient.setUserId
			originalSetUserProperties = amplitudeClient.setUserProperties

			// Replace Amplitude's methods with our interceptors
			amplitudeClient.logEvent = (
				eventName: string,
				eventProperties: any,
			) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.addProperties) {
					highlightMethods.addProperties(eventName, eventProperties)
				}

				// Call original method
				return originalLogEvent?.call(
					amplitudeClient,
					eventName,
					eventProperties,
				)
			}

			amplitudeClient.setUserId = (userId: string) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.identify) {
					highlightMethods.identify(userId, {})
				}

				// Call original method
				return originalSetUserId?.call(amplitudeClient, userId)
			}

			amplitudeClient.setUserProperties = (userProperties: any) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.identify) {
					const userId = 'unknown-user' // Get from current state or use a default
					highlightMethods.identify(userId, userProperties)
				}

				// Call original method
				return originalSetUserProperties?.call(
					amplitudeClient,
					userProperties,
				)
			}
		},

		onHighlight: (methods: any) => {
			highlightMethods = methods
		},

		// For testing
		get _originalLogEvent() {
			return originalLogEvent
		},
		get _originalSetUserId() {
			return originalSetUserId
		},
		get _originalSetUserProperties() {
			return originalSetUserProperties
		},
	}
}

// Mixpanel integration
export const createMixpanelIntegration = () => {
	let highlightMethods: any = null
	let originalTrack: Function | null = null
	let originalIdentify: Function | null = null
	let originalPeopleSet: Function | null = null

	return {
		name: 'Mixpanel',
		register: (mixpanelClient: any) => {
			if (!mixpanelClient) return

			// Store original methods
			originalTrack = mixpanelClient.track
			originalIdentify = mixpanelClient.identify
			originalPeopleSet = mixpanelClient.people?.set

			// Replace Mixpanel's methods with our interceptors
			mixpanelClient.track = (
				eventName: string,
				eventProperties: any,
			) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.addProperties) {
					highlightMethods.addProperties(eventName, eventProperties)
				}

				// Call original method
				return originalTrack?.call(
					mixpanelClient,
					eventName,
					eventProperties,
				)
			}

			mixpanelClient.identify = (userId: string) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.identify) {
					highlightMethods.identify(userId, {})
				}

				// Call original method
				return originalIdentify?.call(mixpanelClient, userId)
			}

			if (mixpanelClient.people) {
				mixpanelClient.people.set = (userProperties: any) => {
					// Call Highlight's method if available
					if (highlightMethods && highlightMethods.identify) {
						const userId = 'unknown-user' // Get from current state or use a default
						highlightMethods.identify(userId, userProperties)
					}

					// Call original method
					return originalPeopleSet?.call(
						mixpanelClient.people,
						userProperties,
					)
				}
			}
		},

		onHighlight: (methods: any) => {
			highlightMethods = methods
		},

		// For testing
		get _originalTrack() {
			return originalTrack
		},
		get _originalIdentify() {
			return originalIdentify
		},
		get _originalPeopleSet() {
			return originalPeopleSet
		},
	}
}

// Segment integration
export const createSegmentIntegration = () => {
	let highlightMethods: any = null
	let originalTrack: Function | null = null
	let originalIdentify: Function | null = null

	return {
		name: 'Segment',
		register: (segmentClient: any) => {
			if (!segmentClient) return

			// Store original methods
			originalTrack = segmentClient.track
			originalIdentify = segmentClient.identify

			// Replace Segment's methods with our interceptors
			segmentClient.track = (eventName: string, eventProperties: any) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.addProperties) {
					highlightMethods.addProperties(eventName, eventProperties)
				}

				// Call original method
				return originalTrack?.call(
					segmentClient,
					eventName,
					eventProperties,
				)
			}

			segmentClient.identify = (userId: string, traits: any) => {
				// Call Highlight's method if available
				if (highlightMethods && highlightMethods.identify) {
					highlightMethods.identify(userId, traits || {})
				}

				// Call original method
				return originalIdentify?.call(segmentClient, userId, traits)
			}
		},

		onHighlight: (methods: any) => {
			highlightMethods = methods
		},

		// For testing
		get _originalTrack() {
			return originalTrack
		},
		get _originalIdentify() {
			return originalIdentify
		},
	}
}
