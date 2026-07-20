// LaunchDarkly React Native symbols-id Metro plugin (Symbols Id Lane).
//
// This wraps Metro's serializer so every release bundle carries a deterministic
// symbols id derived from its composed source map. Two things happen:
//
//   1. Runtime: a fixed-length placeholder module defines
//      `globalThis.__LD_SYMBOLS_ID__`. After the bundle + map are serialized, the
//      placeholder is overwritten IN PLACE with the real id. Same length means
//      Hermes byte offsets don't shift, so the map stays valid. The SDK reads
//      this global and reports it as the resource attribute
//      `launchdarkly.symbols_id.htlhash`.
//   2. Upload: the id is written to a `<map>.symbolsid` sidecar (and logged) so
//      `ldcli symbols upload` keys the uploaded map by the same id.
//
// Key insight (avoid the self-reference trap): the id is `htlhash(source map)`,
// NOT a hash of the bundle. The map does not contain the id, so hashing it is
// stable even though we then inject the id back into the bundle.
//
// It is intentionally defensive: any failure logs a warning and returns the
// original, unmodified serializer output so a broken plugin can never break a
// build (symbolication just falls back to the Version Lane: basename + app
// version).

const crypto = require('crypto')
const fs = require('fs')
const path = require('path')

// 32 hex chars = 16 bytes, the htlhash length. The placeholder is 32 zeros
// so it can be overwritten in place with a real 32-char id without shifting any
// byte offsets in the already-serialized bundle.
const SYMBOLS_ID_LENGTH = 32
const SYMBOLS_ID_PLACEHOLDER = '0'.repeat(SYMBOLS_ID_LENGTH)
const SYMBOLS_ID_GLOBAL = '__LD_SYMBOLS_ID__'
const SYMBOLS_ID_MODULE_PATH = '__launchdarkly_symbols_id__'

// computeHtlhash implements the OTel-recommended deterministic htlhash:
// sha256 over the first 4096 bytes, the last 4096 bytes, and the 8-byte
// little-endian length, truncated to the first 16 bytes (32 hex chars). Small
// inputs are hashed whole. It only needs to be internally consistent: the same
// bytes always yield the same id.
function computeHtlhash(buffer) {
	const HEAD_TAIL = 4096
	const hash = crypto.createHash('sha256')
	if (buffer.length <= HEAD_TAIL * 2) {
		hash.update(buffer)
	} else {
		hash.update(buffer.subarray(0, HEAD_TAIL))
		hash.update(buffer.subarray(buffer.length - HEAD_TAIL))
	}
	const lenBuf = Buffer.alloc(8)
	lenBuf.writeBigUInt64LE(BigInt(buffer.length))
	hash.update(lenBuf)
	return hash.digest('hex').slice(0, SYMBOLS_ID_LENGTH)
}

// createSymbolsIdPreModule builds a virtual Metro module that defines the
// symbols-id global with the placeholder. Prepending it to `preModules` before
// serialization means the source map already accounts for these bytes, so the
// later in-place overwrite (same length) keeps every mapping correct.
function createSymbolsIdPreModule() {
	const code = `globalThis.${SYMBOLS_ID_GLOBAL}=${JSON.stringify(SYMBOLS_ID_PLACEHOLDER)};`
	return {
		dependencies: new Map(),
		getSource: () => Buffer.from(code),
		inverseDependencies: new Set(),
		path: SYMBOLS_ID_MODULE_PATH,
		output: [
			{
				type: 'js/script/virtual',
				data: { code, lineCount: 1, map: [], functionMap: null },
			},
		],
	}
}

// resolveMetroExport unwraps a Metro internal module to the callable it exports.
// Metro's export shapes vary by version: a bare function (older), a Babel
// `.default` export (baseJSBundle/bundleToString on 0.8x), or a named export
// (sourceMapString). Try each so the plugin works across Metro versions.
function resolveMetroExport(mod, namedKey) {
	if (typeof mod === 'function') {
		return mod
	}
	if (mod && typeof mod.default === 'function') {
		return mod.default
	}
	if (namedKey && mod && typeof mod[namedKey] === 'function') {
		return mod[namedKey]
	}
	throw new TypeError(
		`could not resolve callable from Metro module${namedKey ? ` (${namedKey})` : ''}`,
	)
}

// defaultSerialize reproduces Metro's built-in bundle+map output for the case
// where the project has no existing customSerializer. Uses Metro internals the
// same way other RN symbols-id tools do; guarded by the caller's try/catch.
function defaultSerialize(entryPoint, preModules, graph, options) {
	const baseJSBundle = resolveMetroExport(
		require('metro/src/DeltaBundler/Serializers/baseJSBundle'),
	)
	const bundleToString = resolveMetroExport(
		require('metro/src/lib/bundleToString'),
	)
	const sourceMapString = resolveMetroExport(
		require('metro/src/DeltaBundler/Serializers/sourceMapString'),
		'sourceMapString',
	)

	const bundle = baseJSBundle(entryPoint, preModules, graph, options)
	const { code } = bundleToString(bundle)
	const map = sourceMapString(
		[...preModules, ...graph.dependencies.values()],
		{
			excludeSource: options.excludeSource,
			processModuleFilter: options.processModuleFilter,
			shouldAddToIgnoreList: options.shouldAddToIgnoreList,
		},
	)
	return { code, map }
}

// writeSidecar records the id next to the source map so `ldcli symbols upload`
// can read it. The output path is best-effort (Metro doesn't hand the serializer
// the on-disk bundle path): honor LD_SYMBOLS_ID_SIDECAR, else derive from
// options.sourceMapUrl, else skip (the id is still logged and embedded).
function writeSidecar(symbolsId, options) {
	let target = process.env.LD_SYMBOLS_ID_SIDECAR
	if (!target && options && typeof options.sourceMapUrl === 'string') {
		const base = path.basename(options.sourceMapUrl.split('?')[0])
		if (base && base !== '.' && base !== '/') {
			target = path.resolve(process.cwd(), `${base}.symbolsid`)
		}
	}
	if (!target) {
		return
	}
	try {
		fs.mkdirSync(path.dirname(target), { recursive: true })
		fs.writeFileSync(target, symbolsId)
		// eslint-disable-next-line no-console
		console.log(`[LaunchDarkly] wrote symbols id sidecar ${target}`)
	} catch (err) {
		// eslint-disable-next-line no-console
		console.warn(
			`[LaunchDarkly] could not write symbols id sidecar: ${err}`,
		)
	}
}

// withLaunchDarklySymbolsId wraps a Metro config so release bundles are stamped
// with a symbols id. Merge it into metro.config.js:
//
//   const { withLaunchDarklySymbolsId } = require(
//     '@launchdarkly/observability-react-native/metro')
//   module.exports = withLaunchDarklySymbolsId(mergeConfig(getDefaultConfig(__dirname), {}))
function withLaunchDarklySymbolsId(config) {
	const previous =
		config && config.serializer && config.serializer.customSerializer

	const customSerializer = async (entryPoint, preModules, graph, options) => {
		const augmentedPreModules = [createSymbolsIdPreModule(), ...preModules]

		let output
		try {
			output = previous
				? await previous(
						entryPoint,
						augmentedPreModules,
						graph,
						options,
					)
				: defaultSerialize(
						entryPoint,
						augmentedPreModules,
						graph,
						options,
					)
		} catch (err) {
			// eslint-disable-next-line no-console
			console.warn(
				`[LaunchDarkly] symbols-id serializer failed, falling back to the Version Lane: ${err}`,
			)
			// Re-run without our premodule so the build still succeeds untouched.
			return previous
				? previous(entryPoint, preModules, graph, options)
				: defaultSerialize(entryPoint, preModules, graph, options)
		}

		// Normalize to { code, map }. A string result carries no map, so there is
		// nothing to hash — leave it as-is (Version Lane).
		const result =
			typeof output === 'string'
				? { code: output, map: undefined }
				: output
		if (!result || !result.map) {
			return output
		}

		try {
			const symbolsId = computeHtlhash(Buffer.from(result.map))
			if (!result.code.includes(SYMBOLS_ID_PLACEHOLDER)) {
				// eslint-disable-next-line no-console
				console.warn(
					'[LaunchDarkly] symbols-id placeholder missing from bundle; skipping injection',
				)
				return output
			}
			// Overwrite the first placeholder occurrence only; identical length
			// preserves all downstream byte/line offsets and thus the source map.
			const code = result.code.replace(SYMBOLS_ID_PLACEHOLDER, symbolsId)
			writeSidecar(symbolsId, options)
			// eslint-disable-next-line no-console
			console.log(`[LaunchDarkly] bundle symbols id ${symbolsId}`)
			return { ...result, code }
		} catch (err) {
			// eslint-disable-next-line no-console
			console.warn(
				`[LaunchDarkly] could not inject symbols id, using the Version Lane: ${err}`,
			)
			return output
		}
	}

	return {
		...config,
		serializer: {
			...(config && config.serializer),
			customSerializer,
		},
	}
}

module.exports = {
	withLaunchDarklySymbolsId,
	computeHtlhash,
	SYMBOLS_ID_PLACEHOLDER,
	SYMBOLS_ID_GLOBAL,
}
