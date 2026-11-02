import { afterEach, describe, expect, it } from 'vitest'
import { Platform } from 'react-native'
import {
	getPlatformErrorAttributes,
	getPlatformResourceAttributes,
} from './platformAttributes'

const platform = Platform as unknown as {
	OS: string
	Version: number | string
	constants: Record<string, unknown> | undefined
}

// The react-native module is aliased to src/__mocks__/react-native.ts in
// vitest, whose `Platform` is a plain mutable object. Set it up per test and
// restore afterwards.
const original = {
	OS: platform.OS,
	Version: (platform as any).Version,
	constants: (platform as any).constants,
}

afterEach(() => {
	platform.OS = original.OS
	platform.Version = original.Version
	platform.constants = original.constants
})

describe('getPlatformResourceAttributes', () => {
	it('exposes os.* and device.* attributes on iOS', () => {
		platform.OS = 'ios'
		platform.Version = '17.0'
		platform.constants = {
			systemName: 'iOS',
			osVersion: '17.0.1',
			interfaceIdiom: 'phone',
		}

		expect(getPlatformResourceAttributes()).toEqual({
			'os.type': 'darwin',
			'os.name': 'iOS',
			'os.version': '17.0.1',
		})
	})

	it('exposes os.* and device.* attributes on Android', () => {
		platform.OS = 'android'
		platform.Version = 34
		platform.constants = {
			Release: '14',
			Model: 'SM-G991B',
			Brand: 'Samsung',
			Manufacturer: 'Samsung',
		}

		expect(getPlatformResourceAttributes()).toEqual({
			'os.type': 'linux',
			'os.name': 'Android',
			'os.version': '14',
			'device.model.identifier': 'SM-G991B',
			'device.manufacturer': 'Samsung',
		})
	})

	it('falls back to Platform.Version when the OS-specific version is missing', () => {
		platform.OS = 'android'
		platform.Version = 34
		platform.constants = {}

		const attributes = getPlatformResourceAttributes()

		expect(attributes['os.version']).toBe('34')
		// No model / manufacturer info available — the keys are omitted instead
		// of carrying empty values.
		expect(attributes).not.toHaveProperty('device.model.identifier')
		expect(attributes).not.toHaveProperty('device.manufacturer')
	})

	it('always carries os.type and os.name, even with no constants', () => {
		platform.OS = 'android'
		platform.Version = 34
		platform.constants = undefined

		expect(getPlatformResourceAttributes()).toEqual({
			'os.type': 'linux',
			'os.name': 'Android',
			'os.version': '34',
		})
	})
})

describe('getPlatformErrorAttributes', () => {
	it('keeps the legacy platform.* names on iOS error rows', () => {
		platform.OS = 'ios'
		platform.Version = '17.0'
		platform.constants = {
			systemName: 'iOS',
			Model: 'iPhone',
		}

		expect(getPlatformErrorAttributes()).toEqual({
			'platform.os': 'ios',
			'platform.version': '17.0',
			'platform.ios.system_name': 'iOS',
			'platform.ios.model': 'iPhone',
		})
	})

	it('keeps the legacy platform.* names on Android error rows', () => {
		platform.OS = 'android'
		platform.Version = 34
		platform.constants = {
			Brand: 'Samsung',
			Model: 'SM-G991B',
			Release: '14',
		}

		expect(getPlatformErrorAttributes()).toEqual({
			'platform.os': 'android',
			'platform.version': 34,
			'platform.android.brand': 'Samsung',
			'platform.android.model': 'SM-G991B',
			'platform.android.release': '14',
		})
	})

	it('matches the values reported on the Resource', () => {
		platform.OS = 'ios'
		platform.Version = '17.0'
		platform.constants = {
			systemName: 'iOS',
			osVersion: '17.0',
			interfaceIdiom: 'phone',
		}

		const resource = getPlatformResourceAttributes()
		const error = getPlatformErrorAttributes()

		expect(error['platform.ios.system_name']).toBe(resource['os.name'])
		expect(error['platform.version']).toBe(resource['os.version'])
	})
})
