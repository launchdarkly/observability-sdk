import type { Attributes } from '@opentelemetry/api'
import { Platform } from 'react-native'

/**
 * OpenTelemetry attribute names (semantic conventions `os.*` / `device.*`),
 * declared locally because the constants ship in the
 * `@opentelemetry/semantic-conventions` `incubating` subpath, which is not
 * resolvable under this package's `moduleResolution: node` typegen config.
 */
export const ATTR_OS_TYPE = 'os.type'
export const ATTR_OS_NAME = 'os.name'
export const ATTR_OS_VERSION = 'os.version'
export const ATTR_DEVICE_MODEL_IDENTIFIER = 'device.model.identifier'
export const ATTR_DEVICE_MANUFACTURER = 'device.manufacturer'

/**
 * `os.type` well-known values (semantic conventions) for the React Native
 * platforms that have one; `os.name` carries the user-facing name.
 */
const OS_TYPE_BY_PLATFORM: Record<string, string> = {
	ios: 'darwin',
	macos: 'darwin',
	android: 'linux',
	windows: 'windows',
}

/**
 * The platform-specific keys of `Platform.constants`, typed defensively:
 * `Platform.constants` is only typed with the common subset, and real values
 * depend on the platform (iOS: `systemName`/`osVersion`/`interfaceIdiom`;
 * Android: `Release`/`Model`/`Brand`/`Manufacturer`).
 */
type PlatformConstantsExtras = {
	systemName?: string
	osVersion?: string
	interfaceIdiom?: string
	Release?: string
	Model?: string
	Brand?: string
	Manufacturer?: string
}

function getPlatformConstants(): PlatformConstantsExtras | undefined {
	return (Platform as { constants?: PlatformConstantsExtras }).constants
}

function putDefinedString(
	attributes: Attributes,
	key: string,
	value: string | number | boolean | undefined | null,
): void {
	// Resource attributes are descriptive strings, so numbers/booleans (e.g. the
	// Android API level) are stringified and everything else is omitted.
	if (typeof value === 'string') {
		if (value !== '') attributes[key] = value
	} else if (typeof value === 'number' || typeof value === 'boolean') {
		attributes[key] = String(value)
	}
}

/**
 * Process-wide platform attributes for the OTel Resource (semantic conventions
 * `os.*` / `device.*`), read from React Native's `Platform` module. They are
 * constant for the app process, and every log, span, error and metric carries
 * them via the Resource.
 *
 * Only attributes the platform actually exposes are included. Callers merge
 * user `resourceAttributes` after these, so an explicit user value wins.
 */
export function getPlatformResourceAttributes(): Attributes {
	const constants = getPlatformConstants()
	const attributes: Attributes = {}
	putDefinedString(attributes, ATTR_OS_TYPE, OS_TYPE_BY_PLATFORM[Platform.OS])

	if (Platform.OS === 'ios') {
		// Prefer `constants.osVersion`: `Platform.Version` has been exposed as a
		// truncated number on some React Native releases, which would lose the
		// patch version (e.g. an iOS build 26.2 becoming '26').
		putDefinedString(
			attributes,
			ATTR_OS_VERSION,
			constants?.osVersion ?? Platform.Version,
		)
		putDefinedString(attributes, ATTR_OS_NAME, constants?.systemName)
		// React Native does not expose the iOS device model; `interfaceIdiom`
		// is only the device class ('phone' / 'pad'), so it is not reported.
	} else if (Platform.OS === 'android') {
		// `Platform.Version` here is the API level (e.g. 34); `Release` is the
		// human OS version (e.g. '14'), matching the legacy
		// `platform.android.release` error attribute.
		putDefinedString(
			attributes,
			ATTR_OS_VERSION,
			constants?.Release ?? Platform.Version,
		)
		// Android has no systemName counterpart, so use the family name.
		putDefinedString(attributes, ATTR_OS_NAME, 'Android')
		putDefinedString(
			attributes,
			ATTR_DEVICE_MODEL_IDENTIFIER,
			constants?.Model,
		)
		putDefinedString(
			attributes,
			ATTR_DEVICE_MANUFACTURER,
			constants?.Manufacturer,
		)
	}

	return attributes
}

/**
 * Legacy `platform.*` attributes carried on error rows. Kept so previously
 * recorded attributes keep flowing on error rows now that the same platform
 * values are also on the Resource (`os.*` / `device.*`).
 */
export function getPlatformErrorAttributes(): Attributes {
	const constants = getPlatformConstants()
	const attributes: Attributes = {
		'platform.os': Platform.OS,
		'platform.version': Platform.Version,
	}

	if (Platform.OS === 'ios') {
		attributes['platform.ios.system_name'] = constants?.systemName
		attributes['platform.ios.model'] = constants?.Model
	} else if (Platform.OS === 'android') {
		attributes['platform.android.brand'] = constants?.Brand
		attributes['platform.android.model'] = constants?.Model
		attributes['platform.android.release'] = constants?.Release
	}

	return attributes
}
