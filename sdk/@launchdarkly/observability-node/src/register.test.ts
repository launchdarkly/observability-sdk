import { spawnSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

// Exercises the built `@launchdarkly/observability-node/register` entry point
// (dist/register.mjs) in a separate Node.js process.
const packageDir = resolve(__dirname, '..')
const registerPath = join(packageDir, 'dist', 'register.mjs')

const isWrapped = (fn: string) =>
	`typeof ${fn} === 'function' && ${fn}.__wrapped === true`

let appDir: string

function runApp(app: string, preload: boolean) {
	const args = [
		...(preload ? ['--import', registerPath] : []),
		join(appDir, app),
	]
	const result = spawnSync(process.execPath, args, {
		cwd: appDir,
		encoding: 'utf8',
		env: { ...process.env, NODE_OPTIONS: '' },
		timeout: 30_000,
	})
	if (result.status !== 0) {
		throw new Error(
			`node ${args.join(' ')} exited ${result.status}: ${result.stderr}`,
		)
	}
	return JSON.parse(result.stdout.trim().split('\n').pop()!)
}

describe('register (ES modules)', () => {
	beforeAll(() => {
		appDir = mkdtempSync(join(tmpdir(), 'ld-observability-register-'))
		// An ES-module-only `pg`, like the ESM-only libraries in #828: it can
		// only be patched through the import-in-the-middle loader hook.
		const esmPg = join(appDir, 'node_modules', 'pg')
		mkdirSync(esmPg, { recursive: true })
		writeFileSync(
			join(esmPg, 'package.json'),
			JSON.stringify({
				name: 'pg',
				version: '8.13.3',
				type: 'module',
				exports: './index.js',
			}),
		)
		writeFileSync(
			join(esmPg, 'index.js'),
			[
				'export class Client { connect() {} query() {} }',
				'export class Pool {}',
				'export default { Client, Pool }',
			].join('\n'),
		)
		// An ES-module-only `@nestjs/core` (NestJS 12 is ESM-only): its
		// instrumentation patches internal files, which the hook must wrap too.
		const esmNest = join(appDir, 'node_modules', '@nestjs', 'core')
		mkdirSync(join(esmNest, 'router'), { recursive: true })
		writeFileSync(
			join(esmNest, 'package.json'),
			JSON.stringify({
				name: '@nestjs/core',
				version: '11.1.0',
				type: 'module',
				exports: './index.js',
			}),
		)
		writeFileSync(
			join(esmNest, 'index.js'),
			[
				"export * from './nest-factory.js'",
				"export * from './router/router-execution-context.js'",
			].join('\n'),
		)
		writeFileSync(
			join(esmNest, 'nest-factory.js'),
			'export class NestFactoryStatic { async create() {} }',
		)
		writeFileSync(
			join(esmNest, 'router', 'router-execution-context.js'),
			'export class RouterExecutionContext { create() {} }',
		)
		writeFileSync(
			join(appDir, 'nest.mjs'),
			[
				"import { NestFactoryStatic, RouterExecutionContext } from '@nestjs/core'",
				`import '${join(packageDir, 'dist', 'index.cjs')}'`,
				`console.log(JSON.stringify({ factory: ${isWrapped('NestFactoryStatic.prototype.create')}, router: ${isWrapped('RouterExecutionContext.prototype.create')} }))`,
			].join('\n'),
		)
		// The application imports the library before it loads the plugin, as
		// an ES module app does when its module graph imports the database
		// client alongside the module that initializes LaunchDarkly.
		writeFileSync(
			join(appDir, 'app.mjs'),
			[
				"import pg from 'pg'",
				`import '${join(packageDir, 'dist', 'index.cjs')}'`,
				`console.log(JSON.stringify({ wrapped: ${isWrapped('pg.Client.prototype.query')} }))`,
			].join('\n'),
		)
		// The real (CommonJS) pg, imported from an ES module before the plugin.
		writeFileSync(
			join(appDir, 'cjs-pg.mjs'),
			[
				"import { createRequire } from 'node:module'",
				`const req = createRequire('${join(packageDir, 'package.json')}')`,
				"const pg = (await import(req.resolve('pg'))).default",
				`await import('${join(packageDir, 'dist', 'index.cjs')}')`,
				`console.log(JSON.stringify({ wrapped: ${isWrapped('pg.Client.prototype.query')} }))`,
			].join('\n'),
		)
	})

	afterAll(() => {
		rmSync(appDir, { recursive: true, force: true })
	})

	it('does not instrument an ES module library without the hook', () => {
		expect(runApp('app.mjs', false)).toEqual({ wrapped: false })
	})

	it('instruments an ES module library with --import register', () => {
		expect(runApp('app.mjs', true)).toEqual({ wrapped: true })
	})

	it('instruments internal files of an ES module library', () => {
		expect(runApp('nest.mjs', false)).toEqual({
			factory: false,
			router: false,
		})
		expect(runApp('nest.mjs', true)).toEqual({
			factory: true,
			router: true,
		})
	})

	it('instruments a CommonJS library imported before the plugin', () => {
		expect(runApp('cjs-pg.mjs', false)).toEqual({ wrapped: false })
		expect(runApp('cjs-pg.mjs', true)).toEqual({ wrapped: true })
	})
})
