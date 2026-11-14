// CommonJS shim around cross-fetch's browser ponyfill.
//
// cross-fetch ships a UMD ponyfill that reassigns `exports` before setting its
// properties, so Vite's bundler cannot detect its named exports. Re-exporting
// them explicitly gives @rollup/plugin-commonjs something it can analyse
// statically.
const ponyfill = require('cross-fetch/dist/browser-ponyfill.js')

const fetchImpl = ponyfill.fetch || ponyfill

exports.default = fetchImpl
exports.fetch = fetchImpl
exports.Headers = ponyfill.Headers
exports.Request = ponyfill.Request
exports.Response = ponyfill.Response
