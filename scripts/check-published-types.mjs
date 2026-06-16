#!/usr/bin/env node
// Guard against shipping broken TypeScript declarations.
//
// Why this exists:
//   `highlight.run` (and the thin `@launchdarkly/observability` /
//   `@launchdarkly/session-replay` wrappers that re-export it) bundles its
//   implementation into a single rolled-up `dist/index.d.ts`. If that
//   declaration file `import`s a type from a package that is NOT a declared
//   runtime dependency (e.g. a dev-only or workspace package), or from an
//   internal path alias (e.g. `client/types/observe`), the import is
//   unresolvable in a consumer's `node_modules`. Consumers whose tsconfig does
//   not set `skipLibCheck: true` (the TypeScript default is `false`) then get
//   type-check errors just by importing our package.
//
//   The in-repo `tsc` never catches this: inside the monorepo every dev
//   dependency and path alias resolves. The only faithful test is to install
//   the packed tarball into a clean project that has ONLY the declared runtime
//   dependencies, then type-check an import of it.
//
// What this does:
//   1. `npm pack` the freshly built package (uses its `files`/`dist`).
//   2. Install the tarball into a temp project OUTSIDE the monorepo, so npm
//      installs only the package's declared dependencies — exactly what a real
//      consumer gets. No workspace/devDependency leakage.
//   3. Type-check a tiny consumer that imports the public API, with
//      `skipLibCheck: false`, under both `bundler` and classic `node` module
//      resolution.
//
// Run after the build (the declaration file must already exist):
//   node scripts/check-published-types.mjs

import { execFileSync } from 'node:child_process'
import {
	mkdtempSync,
	mkdirSync,
	writeFileSync,
	existsSync,
	rmSync,
} from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..')

// Pin the compiler so the check is deterministic across CI runs.
const TYPESCRIPT_VERSION = '5.8.3'

// Packages to verify. `highlight.run` holds the entire generated type surface;
// the `@launchdarkly/*` wrappers below just re-export from it, so their own
// declaration files are trivial. We test `highlight.run` because all of its
// runtime dependencies are published to npm, so its tarball installs cleanly
// in isolation. `imports` is what the consumer file imports from the package.
const PACKAGES = [
	{
		dir: 'sdk/highlight-run',
		name: 'highlight.run',
		dts: 'dist/index.d.ts',
		consumer: [
			`import { Observe, LDObserve, LDRecord, H } from 'highlight.run'`,
			`import type { ObserveOptions, RecordOptions } from 'highlight.run'`,
			`const _opts: ObserveOptions = {}`,
			`void [Observe, LDObserve, LDRecord, H, _opts]`,
			`export type _RecordOptions = RecordOptions`,
		].join('\n'),
	},
]

// Resolution modes a consumer is likely to use:
//   - `bundler`: the modern Vite / Next / esbuild default.
//   - `node`:    classic Node (node10) resolution, still common.
// Both surface a missing/unresolvable declaration import as TS2307.
//
// `node16`/`nodenext` are intentionally NOT exercised: `@launchdarkly/js-client-sdk`
// (a real, required dependency) currently ships ESM declarations with
// extensionless relative imports, which node16/nodenext reject (TS2834). That
// noise originates in a dependency we do not control here and would mask the
// signal this guard exists to catch.
const RESOLUTION_MODES = {
	bundler: { module: 'ESNext', moduleResolution: 'bundler' },
	node: {
		module: 'CommonJS',
		moduleResolution: 'node',
		esModuleInterop: true,
	},
}

function run(cmd, args, opts = {}) {
	return execFileSync(cmd, args, { encoding: 'utf8', stdio: 'pipe', ...opts })
}

let failed = false

for (const pkg of PACKAGES) {
	const pkgDir = join(repoRoot, pkg.dir)
	const dtsPath = join(pkgDir, pkg.dts)
	if (!existsSync(dtsPath)) {
		console.error(
			`✗ ${pkg.name}: declaration file not found at ${pkg.dts}. ` +
				`Run the build first (it generates dist/**/*.d.ts).`,
		)
		failed = true
		continue
	}

	// Temp project lives outside the repo so npm does not treat it as part of
	// the yarn workspace (which would leak monorepo dependencies).
	const work = mkdtempSync(join(tmpdir(), 'ld-types-check-'))
	try {
		const packOut = run('npm', [
			'pack',
			pkgDir,
			'--pack-destination',
			work,
			'--json',
		])
		const tarball = join(work, JSON.parse(packOut)[0].filename)

		writeFileSync(
			join(work, 'package.json'),
			JSON.stringify(
				{ name: 'consumer', version: '0.0.0', private: true },
				null,
				2,
			),
		)
		// `--ignore-scripts` keeps the check hermetic and fast; we only need the
		// declared dependency tree on disk, not their build steps.
		run(
			'npm',
			[
				'install',
				'--no-audit',
				'--no-fund',
				'--ignore-scripts',
				tarball,
				`typescript@${TYPESCRIPT_VERSION}`,
			],
			{
				cwd: work,
			},
		)

		const srcDir = join(work, 'src')
		mkdirSync(srcDir)
		writeFileSync(join(srcDir, 'index.ts'), pkg.consumer + '\n')

		for (const [mode, modeOptions] of Object.entries(RESOLUTION_MODES)) {
			writeFileSync(
				join(work, 'tsconfig.json'),
				JSON.stringify(
					{
						compilerOptions: {
							target: 'ESNext',
							strict: true,
							noEmit: true,
							// The whole point: do NOT skip checking library
							// (node_modules) declaration files.
							skipLibCheck: false,
							lib: ['ESNext', 'DOM', 'DOM.Iterable'],
							...modeOptions,
						},
						include: ['src'],
					},
					null,
					2,
				),
			)
			try {
				run(
					join(work, 'node_modules', '.bin', 'tsc'),
					['--project', 'tsconfig.json'],
					{
						cwd: work,
					},
				)
				console.log(
					`✓ ${pkg.name}: type-checks cleanly (moduleResolution: ${mode})`,
				)
			} catch (err) {
				failed = true
				const out = `${err.stdout || ''}${err.stderr || ''}`.trim()
				console.error(
					`✗ ${pkg.name}: consumer type-check FAILED (moduleResolution: ${mode}).\n` +
						`  A published .d.ts imports something that is not resolvable in a clean install.\n` +
						out.replace(/^/gm, '  '),
				)
			}
		}
	} finally {
		rmSync(work, { recursive: true, force: true })
	}
}

if (failed) {
	console.error('\nPublished-type check failed. See errors above.')
	process.exit(1)
}
console.log('\nAll published types resolve in a clean consumer install.')
