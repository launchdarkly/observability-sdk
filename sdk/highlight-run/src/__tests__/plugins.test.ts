import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import * as observePlugin from '../__mocks__/plugins'
import * as recordPlugin from '../__mocks__/plugins'
import * as common from '../__mocks__/plugins'

describe('Plugins', () => {
	const originalWindow = global.window
	const originalDocument = global.document

	beforeEach(() => {
		// Mock window
		global.window = {
			...originalWindow,
			addEventListener: vi.fn(),
			removeEventListener: vi.fn(),
			location: {
				href: 'https://example.com',
				hostname: 'example.com',
			},
		} as any

		// Mock document
		global.document = {
			...originalDocument,
			addEventListener: vi.fn(),
			removeEventListener: vi.fn(),
			readyState: 'complete',
		} as any
	})

	afterEach(() => {
		global.window = originalWindow
		global.document = originalDocument
		vi.clearAllMocks()
	})

	describe('Common Plugin', () => {
		it('should provide plugin versions', () => {
			const version = common.getPluginVersion()
			expect(version).toBeDefined()
			expect(version).toBeTypeOf('string')
		})

		it('should initialize plugin with default options', () => {
			const plugin = common.createPlugin({})
			expect(plugin).toBeDefined()
			expect(plugin.name).toBeDefined()
			expect(plugin.version).toBeDefined()
		})

		it('should initialize plugin with custom options', () => {
			const customOptions = {
				debug: true,
				environmentKey: 'test-environment',
			}

			const plugin = common.createPlugin(customOptions)
			expect(plugin.options).toMatchObject(customOptions)
		})

		it('should validate plugin name and version', () => {
			const plugin = common.createPlugin({})
			expect(plugin.name).toBeTruthy()
			expect(plugin.version).toBeTruthy()
		})
	})

	describe('Observe Plugin', () => {
		it('should create an observe plugin', () => {
			const plugin = observePlugin.createObservePlugin({
				projectId: 'test-project',
				debug: true,
			})

			expect(plugin).toBeDefined()
			expect(plugin.name).toBe('@highlight-run/observe')
			expect(plugin.load).toBeTypeOf('function')
			expect(plugin.setup).toBeTypeOf('function')
		})

		it('should validate observe plugin required options', () => {
			expect(() => {
				observePlugin.createObservePlugin({} as any)
			}).toThrow()

			expect(() => {
				observePlugin.createObservePlugin({
					projectId: 'test-project',
				})
			}).not.toThrow()
		})

		it('should initialize observe plugin with session ID', () => {
			const plugin = observePlugin.createObservePlugin({
				projectId: 'test-project',
				sessionId: 'test-session',
			})

			expect(plugin.options.sessionId).toBe('test-session')
		})

		it('should initialize observe plugin with OTLP endpoint', () => {
			const plugin = observePlugin.createObservePlugin({
				projectId: 'test-project',
				otlpEndpoint: 'https://custom-otlp.example.com',
			})

			expect(plugin.options.otlpEndpoint).toBe(
				'https://custom-otlp.example.com',
			)
		})

		it('should handle observe plugin lifecycle methods', () => {
			const plugin = observePlugin.createObservePlugin({
				projectId: 'test-project',
			})

			// Mock internal methods
			const loadSpy = vi.fn()
			const setupSpy = vi.fn()

			// Replace methods with spies
			plugin._load = loadSpy
			plugin._setup = setupSpy

			// Call lifecycle methods
			plugin.load({} as any)
			plugin.setup()

			expect(loadSpy).toHaveBeenCalled()
			expect(setupSpy).toHaveBeenCalled()
		})
	})

	describe('Record Plugin', () => {
		it('should create a record plugin', () => {
			const plugin = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
			})

			expect(plugin).toBeDefined()
			expect(plugin.name).toBe('@highlight-run/record')
			expect(plugin.load).toBeTypeOf('function')
			expect(plugin.setup).toBeTypeOf('function')
		})

		it('should validate record plugin required options', () => {
			expect(() => {
				recordPlugin.createRecordPlugin({} as any)
			}).toThrow()

			expect(() => {
				recordPlugin.createRecordPlugin({
					projectId: 'test-project',
				})
			}).not.toThrow()
		})

		it('should initialize record plugin with session ID', () => {
			const plugin = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
				sessionId: 'test-session',
			})

			expect(plugin.options.sessionId).toBe('test-session')
		})

		it('should initialize record plugin with custom backend URL', () => {
			const plugin = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
				backendUrl: 'https://custom-api.example.com',
			})

			expect(plugin.options.backendUrl).toBe(
				'https://custom-api.example.com',
			)
		})

		it('should handle record plugin lifecycle methods', () => {
			const plugin = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
			})

			// Mock internal methods
			const loadSpy = vi.fn()
			const setupSpy = vi.fn()

			// Replace methods with spies
			plugin._load = loadSpy
			plugin._setup = setupSpy

			// Call lifecycle methods
			plugin.load({} as any)
			plugin.setup()

			expect(loadSpy).toHaveBeenCalled()
			expect(setupSpy).toHaveBeenCalled()
		})

		it('should initialize record plugin with network recording options', () => {
			const plugin = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
				networkRecording: false,
			})

			expect(plugin.options.networkRecording).toBe(false)

			const pluginWithNetworkRecording = recordPlugin.createRecordPlugin({
				projectId: 'test-project',
				networkRecording: true,
			})

			expect(pluginWithNetworkRecording.options.networkRecording).toBe(
				true,
			)
		})
	})
})
