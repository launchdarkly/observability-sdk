/**
 * LaunchDarkly React Native symbols-id Metro plugin (Symbols Id Lane).
 *
 * Wraps a Metro config so release bundles are stamped with a deterministic
 * symbols id (htlhash of the composed source map). The SDK reports it as the
 * resource attribute `launchdarkly.symbols_id.htlhash`, and
 * `ldcli symbols upload` keys the uploaded map by the same id, so symbolication
 * resolves by symbols id independent of the bundle filename or app version.
 */

/** Minimal shape of the fields this plugin reads/writes on a Metro config. */
export interface MetroConfigWithSerializer {
	serializer?: {
		customSerializer?: (...args: any[]) => unknown
		[key: string]: unknown
	}
	[key: string]: unknown
}

/**
 * Merge into `metro.config.js`:
 *
 * ```js
 * const { withLaunchDarklySymbolsId } = require(
 *   '@launchdarkly/observability-react-native/metro')
 * module.exports = withLaunchDarklySymbolsId(config)
 * ```
 */
export function withLaunchDarklySymbolsId<T extends MetroConfigWithSerializer>(
	config: T,
): T

/**
 * The OTel htlhash of a buffer: sha256 over head(4096) + tail(4096) + LE length,
 * truncated to 16 bytes (32 hex chars). Exported for testing.
 */
export function computeHtlhash(buffer: Buffer): string

/** The fixed-length (32 zeros) placeholder reserved in the bundle. */
export const SYMBOLS_ID_PLACEHOLDER: string

/** The global the SDK reads at runtime (`__LD_SYMBOLS_ID__`). */
export const SYMBOLS_ID_GLOBAL: string
