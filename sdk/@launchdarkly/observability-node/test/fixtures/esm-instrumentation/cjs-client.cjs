// CommonJS database client: loads `pg` with `require()`, the path that
// require-in-the-middle has always instrumented.
const pg = require('pg')

exports.runQuery = async function runQuery() {
	const client = new pg.Client({
		host: '127.0.0.1',
		port: Number(process.env.PG_PORT),
		user: 'test',
		database: 'test',
	})
	await client.connect()
	try {
		return await client.query('SELECT 1')
	} finally {
		await client.end()
	}
}
