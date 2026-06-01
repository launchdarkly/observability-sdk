import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { Observability } from './observability'
import { _LDObserve } from '../sdk/LDObserve'
import type {
	IdentifySeriesContext,
	LDContext,
	LDPluginEnvironmentMetadata,
	TrackSeriesContext,
} from '@launchdarkly/react-native-client-sdk'

const baseMetadata: LDPluginEnvironmentMetadata = {
	sdk: {
		name: '@launchdarkly/react-native-client-sdk',
		version: '9.9.9',
	},
	mobileKey: 'test-mobile-key',
	application: {
		id: 'test-app',
		version: '4.5.6',
	},
}

describe('Observability plugin hook', () => {
	beforeEach(() => {
		_LDObserve._resetForTesting()
	})

	afterEach(() => {
		vi.restoreAllMocks()
	})

	it('afterTrack delegates to _LDObserve.track with key/data/metricValue', () => {
		const plugin = new Observability()
		const [hook] = plugin.getHooks!(baseMetadata)

		const trackSpy = vi
			.spyOn(_LDObserve, 'track')
			.mockImplementation(() => {})

		const hookContext: TrackSeriesContext = {
			key: 'button.click',
			context: { kind: 'user', key: 'u1' },
			data: { foo: 'bar' },
			metricValue: 17,
		}

		hook.afterTrack?.(hookContext)

		expect(trackSpy).toHaveBeenCalledTimes(1)
		expect(trackSpy).toHaveBeenCalledWith(
			'button.click',
			{ foo: 'bar' },
			17,
		)
	})

	it('afterTrack passes through undefined data and metricValue verbatim', () => {
		const plugin = new Observability()
		const [hook] = plugin.getHooks!(baseMetadata)

		const trackSpy = vi
			.spyOn(_LDObserve, 'track')
			.mockImplementation(() => {})

		const hookContext: TrackSeriesContext = {
			key: 'plain.event',
			context: { kind: 'user', key: 'u1' },
		}

		hook.afterTrack?.(hookContext)

		expect(trackSpy).toHaveBeenCalledTimes(1)
		expect(trackSpy).toHaveBeenCalledWith(
			'plain.event',
			undefined,
			undefined,
		)
	})

	it('afterTrack does not throw when _LDObserve.track throws', () => {
		const plugin = new Observability()
		const [hook] = plugin.getHooks!(baseMetadata)

		vi.spyOn(_LDObserve, 'track').mockImplementation(() => {
			throw new Error('boom')
		})

		const hookContext: TrackSeriesContext = {
			key: 'crash.event',
			context: { kind: 'user', key: 'u1' },
		}

		expect(() => hook.afterTrack?.(hookContext)).not.toThrow()
	})

	it('afterIdentify caches LD context-key attributes on the LDObserve impl', () => {
		const plugin = new Observability()
		const [hook] = plugin.getHooks!(baseMetadata)

		const setLDContextKeyAttributesSpy = vi
			.spyOn(_LDObserve, '_setLDContextKeyAttributes')
			.mockImplementation(() => {})

		const ldContext: LDContext = {
			kind: 'multi',
			user: { key: 'alice' },
			org: { key: 'team-a' },
		} as LDContext

		const identifyContext: IdentifySeriesContext = {
			context: ldContext,
			timeout: 5,
		}

		hook.afterIdentify?.(identifyContext, {}, { status: 'completed' })

		expect(setLDContextKeyAttributesSpy).toHaveBeenCalledTimes(1)
		expect(setLDContextKeyAttributesSpy).toHaveBeenCalledWith({
			user: 'alice',
			org: 'team-a',
		})
	})

	it('register seeds the meta-attributes on the LDObserve impl', () => {
		const plugin = new Observability()

		const setMetaAttributesSpy = vi
			.spyOn(_LDObserve, '_setMetaAttributes')
			.mockImplementation(() => {})

		plugin.getHooks!(baseMetadata)

		expect(setMetaAttributesSpy).toHaveBeenCalledTimes(1)
		const attrs = setMetaAttributesSpy.mock.calls[0][0]
		expect(attrs).toMatchObject({
			'telemetry.sdk.name': '@launchdarkly/observability-react-native',
			'telemetry.sdk.version': '9.9.9',
			'feature_flag.provider.name': 'LaunchDarkly',
			'feature_flag.set.id': 'test-mobile-key',
			'launchdarkly.application.id': 'test-app',
			'launchdarkly.application.version': '4.5.6',
		})
	})
})
