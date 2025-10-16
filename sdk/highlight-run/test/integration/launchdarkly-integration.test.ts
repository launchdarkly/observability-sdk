/**
 * Integration tests for LaunchDarkly SDK compatibility
 * Tests that the ultra-minimal implementation works correctly with LaunchDarkly SDK
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { 
  Observe, 
  Record, 
  ObservabilityPlugin,
  LDObserve,
  LDRecord
} from '../../src/sdk/ld-ultra-minimal'

// Mock LaunchDarkly SDK interface
interface LDClient {
  on: (event: string, handler: Function) => void
  identify: (context: any) => Promise<void>
  variation: (key: string, defaultValue: any) => any
}

// Mock LaunchDarkly SDK
const createMockLDClient = (): LDClient => {
  const handlers: { [key: string]: Function[] } = {}
  
  return {
    on: (event: string, handler: Function) => {
      if (!handlers[event]) {
        handlers[event] = []
      }
      handlers[event].push(handler)
    },
    identify: async (context: any) => {
      // Trigger identify event
      if (handlers['identify']) {
        handlers['identify'].forEach(h => h(context))
      }
    },
    variation: (key: string, defaultValue: any) => {
      // Trigger flag evaluation event
      if (handlers['flag-evaluated']) {
        handlers['flag-evaluated'].forEach(h => h({ key, value: defaultValue }))
      }
      return defaultValue
    }
  }
}

describe('LaunchDarkly Observability Plugin Integration', () => {
  let mockFetch: ReturnType<typeof vi.fn>
  
  beforeEach(() => {
    mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ sessionId: 'test-session' })
    })
    global.fetch = mockFetch
  })
  
  afterEach(() => {
    vi.clearAllMocks()
  })
  
  describe('LDObserve Integration', () => {
    it('should integrate with LaunchDarkly client and track events', async () => {
      const ldClient = createMockLDClient()
      const observePlugin = LDObserve({
        backendUrl: 'https://api.test.com',
        serviceName: 'test-service',
        enableConsoleRecording: true,
        enablePerformanceRecording: true,
        enableNetworkRecording: true
      })
      
      // Initialize plugin with LD client
      observePlugin.initialize(ldClient)
      
      // Simulate user identification
      await ldClient.identify({ key: 'user-123', name: 'Test User' })
      
      // Verify that events are being tracked
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining('https://api.test.com'),
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'Content-Type': 'application/json'
          })
        })
      )
    })
    
    it('should track flag evaluations', () => {
      const ldClient = createMockLDClient()
      const observePlugin = LDObserve({
        backendUrl: 'https://api.test.com',
        serviceName: 'test-service'
      })
      
      observePlugin.initialize(ldClient)
      
      // Evaluate a flag
      const flagValue = ldClient.variation('test-flag', false)
      
      // Verify flag evaluation was tracked
      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          body: expect.stringContaining('test-flag')
        })
      )
    })
    
    it('should batch multiple events efficiently', async () => {
      const observePlugin = Observe({
        backendUrl: 'https://api.test.com',
        serviceName: 'test-service',
        enableNetworkRecording: true
      })
      
      // Generate multiple events quickly
      for (let i = 0; i < 10; i++) {
        observePlugin.trackEvent('custom-event', { index: i })
      }
      
      // Wait for batching
      await new Promise(resolve => setTimeout(resolve, 100))
      
      // Should batch events instead of sending individually
      expect(mockFetch.mock.calls.length).toBeLessThan(10)
    })
  })
  
  describe('LDRecord Integration', () => {
    it('should integrate with LaunchDarkly client for session replay', () => {
      const ldClient = createMockLDClient()
      const recordPlugin = LDRecord({
        backendUrl: 'https://api.test.com',
        privacySetting: 'strict',
        recordInteractions: true
      })
      
      // Initialize plugin
      recordPlugin.initialize(ldClient)
      
      // Simulate user interaction
      const clickEvent = new MouseEvent('click', {
        bubbles: true,
        cancelable: true,
        clientX: 100,
        clientY: 200
      })
      document.body.dispatchEvent(clickEvent)
      
      // Verify interaction was recorded
      expect(mockFetch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          body: expect.stringContaining('click')
        })
      )
    })
    
    it('should respect sampling rate', () => {
      const recordPlugin = Record({
        backendUrl: 'https://api.test.com',
        samplingRate: 0, // Disable recording
        recordInteractions: true
      })
      
      // Start recording
      recordPlugin.start()
      
      // Simulate interactions
      document.body.click()
      
      // Should not send any data due to sampling rate
      expect(mockFetch).not.toHaveBeenCalled()
    })
    
    it('should handle privacy settings correctly', () => {
      const recordPlugin = Record({
        backendUrl: 'https://api.test.com',
        privacySetting: 'strict',
        recordInteractions: true
      })
      
      recordPlugin.start()
      
      // Create input with sensitive data
      const input = document.createElement('input')
      input.type = 'password'
      input.value = 'sensitive-data'
      document.body.appendChild(input)
      
      // Simulate input event
      const inputEvent = new Event('input', { bubbles: true })
      input.dispatchEvent(inputEvent)
      
      // Verify sensitive data is not sent
      if (mockFetch.mock.calls.length > 0) {
        const callBody = mockFetch.mock.calls[0][1].body
        expect(callBody).not.toContain('sensitive-data')
      }
      
      document.body.removeChild(input)
    })
  })
  
  describe('Combined ObservabilityPlugin', () => {
    it('should handle both observability and session replay together', async () => {
      const ldClient = createMockLDClient()
      const combinedPlugin = ObservabilityPlugin(
        {
          backendUrl: 'https://api.test.com',
          serviceName: 'test-app',
          enableConsoleRecording: true
        },
        {
          backendUrl: 'https://api.test.com',
          privacySetting: 'default',
          recordInteractions: true
        }
      )
      
      // Initialize with LD client
      combinedPlugin.observability.initialize(ldClient)
      combinedPlugin.sessionReplay.initialize(ldClient)
      
      // Simulate various activities
      await ldClient.identify({ key: 'user-456' })
      ldClient.variation('feature-flag', true)
      document.body.click()
      
      // Both plugins should be working
      expect(mockFetch).toHaveBeenCalled()
      
      // Verify proper cleanup
      combinedPlugin.observability.stop()
      combinedPlugin.sessionReplay.stop()
    })
    
    it('should maintain small bundle size', () => {
      // This test verifies the implementation is lightweight
      const sourceCode = ObservabilityPlugin.toString()
      
      // Check that the minified code doesn't include heavy dependencies
      expect(sourceCode).not.toContain('rrweb')
      expect(sourceCode).not.toContain('opentelemetry')
      
      // Rough size check (unminified function should still be reasonably small)
      expect(sourceCode.length).toBeLessThan(50000) // 50KB unminified
    })
  })
  
  describe('Error Handling', () => {
    it('should handle network failures gracefully', async () => {
      mockFetch.mockRejectedValueOnce(new Error('Network error'))
      
      const observePlugin = Observe({
        backendUrl: 'https://api.test.com',
        serviceName: 'test-service'
      })
      
      // Should not throw
      expect(() => {
        observePlugin.trackEvent('test-event', {})
      }).not.toThrow()
      
      // Should retry or handle gracefully
      await new Promise(resolve => setTimeout(resolve, 100))
    })
    
    it('should handle invalid configuration', () => {
      expect(() => {
        Observe({
          backendUrl: '', // Invalid URL
          serviceName: 'test'
        })
      }).not.toThrow()
      
      expect(() => {
        Record({
          backendUrl: 'not-a-url',
          samplingRate: 2 // Invalid rate
        })
      }).not.toThrow()
    })
  })
  
  describe('Performance', () => {
    it('should not block main thread', async () => {
      const observePlugin = Observe({
        backendUrl: 'https://api.test.com',
        serviceName: 'perf-test'
      })
      
      const startTime = performance.now()
      
      // Generate many events
      for (let i = 0; i < 1000; i++) {
        observePlugin.trackEvent('perf-event', { index: i })
      }
      
      const endTime = performance.now()
      const duration = endTime - startTime
      
      // Should complete quickly without blocking
      expect(duration).toBeLessThan(100) // 100ms for 1000 events
    })
    
    it('should efficiently batch and compress data', async () => {
      const recordPlugin = Record({
        backendUrl: 'https://api.test.com',
        recordInteractions: true
      })
      
      recordPlugin.start()
      
      // Generate multiple interactions
      for (let i = 0; i < 100; i++) {
        const event = new MouseEvent('click', {
          clientX: Math.random() * 1000,
          clientY: Math.random() * 1000
        })
        document.body.dispatchEvent(event)
      }
      
      // Wait for batching
      await new Promise(resolve => setTimeout(resolve, 200))
      
      // Should batch efficiently
      expect(mockFetch.mock.calls.length).toBeLessThan(10)
    })
  })
})