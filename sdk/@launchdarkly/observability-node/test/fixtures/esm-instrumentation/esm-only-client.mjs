// Stands in for an ESM-only database client such as MikroORM 7: it loads `pg`
// with `import`, so require-in-the-middle never sees it.
import pg from 'pg'

export async function runQuery() {
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
