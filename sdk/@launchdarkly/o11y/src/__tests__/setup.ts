import { vi } from 'vitest'
import '@vitest/web-worker'
import 'vitest-canvas-mock'

// `@vitest/web-worker` polyfills Web Workers by loading the worker's source
// module, but it cannot resolve workers constructed from a `blob:` URL. Such
// workers are produced both by Vite's `?worker&inline` output and by bundled
// dependencies (e.g. rrweb's CanvasManager, which spins up a canvas worker the
// moment `Highlight.initialize` calls `record`). Loading a blob URL throws
// `Cannot find package 'blob:...'`; because these workers are created eagerly,
// the rejection is logged via `console.error` and races vitest's environment
// teardown, surfacing as
// `EnvironmentTeardownError: Closing rpc while "onUserConsoleLog" was pending`
// and failing the whole suite. The unit tests don't exercise these workers, so
// swap blob-backed workers for an inert stub while leaving module-URL workers
// (used by highlight-client-worker.test.ts) on the real implementation.
const RealWorker = globalThis.Worker

class InertWorker extends EventTarget implements Worker {
	onmessage: ((this: Worker, ev: MessageEvent) => unknown) | null = null
	onmessageerror: ((this: Worker, ev: MessageEvent) => unknown) | null = null
	onerror: ((this: AbstractWorker, ev: ErrorEvent) => unknown) | null = null
	postMessage(): void {}
	terminate(): void {}
}

globalThis.Worker = new Proxy(RealWorker, {
	construct(target, args) {
		if (String(args[0] ?? '').startsWith('blob:')) {
			return new InertWorker()
		}
		return Reflect.construct(target, args)
	},
}) as typeof Worker

vi.mock('import.meta.env.REACT_APP_PUBLIC_GRAPH_URI', () => ({
	default: 'localhost:8082',
}))
