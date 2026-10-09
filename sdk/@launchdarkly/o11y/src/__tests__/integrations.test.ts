import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import * as amplitudeIntegration from '../__mocks__/integrations'
import * as mixpanelIntegration from '../__mocks__/integrations'
import * as segmentIntegration from '../__mocks__/integrations'

describe('Integrations', () => {
	// Mock console to prevent noise
	const originalConsole = global.console

	beforeEach(() => {
		global.console = {
			...console,
			log: vi.fn(),
			warn: vi.fn(),
			error: vi.fn(),
		}
	})

	afterEach(() => {
		global.console = originalConsole
		vi.clearAllMocks()
	})

	describe('Amplitude Integration', () => {
		it('should create Amplitude integration', () => {
			const integration =
				amplitudeIntegration.createAmplitudeIntegration()

			expect(integration).toBeDefined()
			expect(integration.name).toBe('Amplitude')
			expect(integration.register).toBeTypeOf('function')
		})

		it('should register with Amplitude client', () => {
			const integration =
				amplitudeIntegration.createAmplitudeIntegration()

			const amplitudeClient = {
				logEvent: vi.fn(),
				setUserId: vi.fn(),
				setUserProperties: vi.fn(),
				init: vi.fn(),
			}

			// Register Amplitude
			integration.register(amplitudeClient as any)

			// Original methods should be stored
			expect((integration as any)._originalLogEvent).toBeDefined()
			expect((integration as any)._originalSetUserId).toBeDefined()
			expect(
				(integration as any)._originalSetUserProperties,
			).toBeDefined()

			// Methods should be replaced with our versions
			expect(amplitudeClient.logEvent).not.toBe(
				(integration as any)._originalLogEvent,
			)
			expect(amplitudeClient.setUserId).not.toBe(
				(integration as any)._originalSetUserId,
			)
			expect(amplitudeClient.setUserProperties).not.toBe(
				(integration as any)._originalSetUserProperties,
			)
		})

		it('should handle Amplitude events and pass to Highlight', () => {
			const integration =
				amplitudeIntegration.createAmplitudeIntegration()

			// Mock Highlight methods
			const highlightMethods = {
				addProperties: vi.fn(),
				identify: vi.fn(),
			}

			// Mock Amplitude client
			const amplitudeClient = {
				logEvent: vi.fn(),
				setUserId: vi.fn(),
				setUserProperties: vi.fn(),
				init: vi.fn(),
			}

			// Register with mock Highlight methods
			integration.onHighlight(highlightMethods as any)

			// Register Amplitude
			integration.register(amplitudeClient as any)

			// Test event tracking
			const event = 'test_event'
			const properties = { key: 'value' }
			amplitudeClient.logEvent(event, properties)

			// Should call Highlight's addProperties
			expect(highlightMethods.addProperties).toHaveBeenCalledWith(
				event,
				properties,
			)

			// Test user identification
			const userId = 'user123'
			amplitudeClient.setUserId(userId)

			// Should call Highlight's identify
			expect(highlightMethods.identify).toHaveBeenCalledWith(userId, {})

			// Test user properties
			const userProps = { role: 'admin', plan: 'premium' }
			amplitudeClient.setUserProperties(userProps)

			// Should call Highlight's identify with user properties
			expect(highlightMethods.identify).toHaveBeenCalledWith(
				expect.any(String),
				userProps,
			)
		})

		it('should handle invalid Amplitude clients', () => {
			const integration =
				amplitudeIntegration.createAmplitudeIntegration()

			// Should not throw when registering null
			expect(() => integration.register(null as any)).not.toThrow()

			// Should not throw when registering incomplete client
			expect(() => integration.register({} as any)).not.toThrow()
		})
	})

	describe('Mixpanel Integration', () => {
		it('should create Mixpanel integration', () => {
			const integration = mixpanelIntegration.createMixpanelIntegration()

			expect(integration).toBeDefined()
			expect(integration.name).toBe('Mixpanel')
			expect(integration.register).toBeTypeOf('function')
		})

		it('should register with Mixpanel client', () => {
			const integration = mixpanelIntegration.createMixpanelIntegration()

			const mixpanelClient = {
				track: vi.fn(),
				identify: vi.fn(),
				people: {
					set: vi.fn(),
				},
			}

			// Register Mixpanel
			integration.register(mixpanelClient as any)

			// Original methods should be stored
			expect((integration as any)._originalTrack).toBeDefined()
			expect((integration as any)._originalIdentify).toBeDefined()
			expect((integration as any)._originalPeopleSet).toBeDefined()

			// Methods should be replaced with our versions
			expect(mixpanelClient.track).not.toBe(
				(integration as any)._originalTrack,
			)
			expect(mixpanelClient.identify).not.toBe(
				(integration as any)._originalIdentify,
			)
			expect(mixpanelClient.people.set).not.toBe(
				(integration as any)._originalPeopleSet,
			)
		})

		it('should handle Mixpanel events and pass to Highlight', () => {
			const integration = mixpanelIntegration.createMixpanelIntegration()

			// Mock Highlight methods
			const highlightMethods = {
				addProperties: vi.fn(),
				identify: vi.fn(),
			}

			// Mock Mixpanel client
			const mixpanelClient = {
				track: vi.fn(),
				identify: vi.fn(),
				people: {
					set: vi.fn(),
				},
			}

			// Register with mock Highlight methods
			integration.onHighlight(highlightMethods as any)

			// Register Mixpanel
			integration.register(mixpanelClient as any)

			// Test event tracking
			const event = 'test_event'
			const properties = { key: 'value' }
			mixpanelClient.track(event, properties)

			// Should call Highlight's addProperties
			expect(highlightMethods.addProperties).toHaveBeenCalledWith(
				event,
				properties,
			)

			// Test user identification
			const userId = 'user123'
			mixpanelClient.identify(userId)

			// Should call Highlight's identify
			expect(highlightMethods.identify).toHaveBeenCalledWith(userId, {})

			// Test user properties
			const userProps = { role: 'admin', plan: 'premium' }
			mixpanelClient.people.set(userProps)

			// Should call Highlight's identify with user properties
			expect(highlightMethods.identify).toHaveBeenCalledWith(
				expect.any(String),
				userProps,
			)
		})

		it('should handle invalid Mixpanel clients', () => {
			const integration = mixpanelIntegration.createMixpanelIntegration()

			// Should not throw when registering null
			expect(() => integration.register(null as any)).not.toThrow()

			// Should not throw when registering incomplete client
			expect(() => integration.register({} as any)).not.toThrow()

			// Should not throw when registering client with missing people
			expect(() =>
				integration.register({
					track: vi.fn(),
					identify: vi.fn(),
				} as any),
			).not.toThrow()
		})
	})

	describe('Segment Integration', () => {
		it('should create Segment integration', () => {
			const integration = segmentIntegration.createSegmentIntegration()

			expect(integration).toBeDefined()
			expect(integration.name).toBe('Segment')
			expect(integration.register).toBeTypeOf('function')
		})

		it('should register with Segment client', () => {
			const integration = segmentIntegration.createSegmentIntegration()

			const segmentClient = {
				track: vi.fn(),
				identify: vi.fn(),
			}

			// Register Segment
			integration.register(segmentClient as any)

			// Original methods should be stored
			expect((integration as any)._originalTrack).toBeDefined()
			expect((integration as any)._originalIdentify).toBeDefined()

			// Methods should be replaced with our versions
			expect(segmentClient.track).not.toBe(
				(integration as any)._originalTrack,
			)
			expect(segmentClient.identify).not.toBe(
				(integration as any)._originalIdentify,
			)
		})

		it('should handle Segment events and pass to Highlight', () => {
			const integration = segmentIntegration.createSegmentIntegration()

			// Mock Highlight methods
			const highlightMethods = {
				addProperties: vi.fn(),
				identify: vi.fn(),
			}

			// Mock Segment client
			const segmentClient = {
				track: vi.fn(),
				identify: vi.fn(),
			}

			// Register with mock Highlight methods
			integration.onHighlight(highlightMethods as any)

			// Register Segment
			integration.register(segmentClient as any)

			// Test event tracking
			const event = 'test_event'
			const properties = { key: 'value' }
			segmentClient.track(event, properties)

			// Should call Highlight's addProperties
			expect(highlightMethods.addProperties).toHaveBeenCalledWith(
				event,
				properties,
			)

			// Test user identification
			const userId = 'user123'
			const traits = { name: 'Test User', email: 'test@example.com' }
			segmentClient.identify(userId, traits)

			// Should call Highlight's identify
			expect(highlightMethods.identify).toHaveBeenCalledWith(
				userId,
				traits,
			)
		})

		it('should handle invalid Segment clients', () => {
			const integration = segmentIntegration.createSegmentIntegration()

			// Should not throw when registering null
			expect(() => integration.register(null as any)).not.toThrow()

			// Should not throw when registering incomplete client
			expect(() => integration.register({} as any)).not.toThrow()
		})
	})
})
