// CommonJS shim around cross-fetch's browser ponyfill.
//
// cross-fetch ships a UMD ponyfill that reassigns `exports` before setting its
// properties, so bundlers cannot statically analyse its named exports. Routing
// cross-fetch through this shim instead gives @rollup/plugin-commonjs a
// statically analysable surface.
//
// `module.exports` must BE the fetch function, not an object carrying it:
// Rollup's CommonJS interop binds a default import to `module.exports` (unless
// `__esModule` is set), so exporting `{ default, fetch, ... }` leaves
// `graphql-request`'s `import fetch from 'cross-fetch'` holding a non-callable
// object and every request throws `fetch is not a function`.
//
// The named constructors are resolved from the ponyfill (cross-fetch 3.x
// attaches them to its function export) and fall back to the platform globals,
// so `Headers`/`Request`/`Response` are never `undefined` at runtime even if a
// ponyfill version only exports the bare `fetch` function.
const ponyfill = require('cross-fetch/dist/browser-ponyfill.js')

const root =
	typeof globalThis !== 'undefined'
		? globalThis
		: typeof self !== 'undefined'
			? self
			: {}

const fetchImpl = ponyfill.default || ponyfill.fetch || ponyfill
const Headers = ponyfill.Headers || root.Headers
const Request = ponyfill.Request || root.Request
const Response = ponyfill.Response || root.Response

module.exports = fetchImpl
module.exports.default = fetchImpl
module.exports.fetch = fetchImpl
module.exports.Headers = Headers
module.exports.Request = Request
module.exports.Response = Response
