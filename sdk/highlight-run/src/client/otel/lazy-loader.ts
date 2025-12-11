/**
 * Lazy loading wrapper for OpenTelemetry features
 * O11Y-393: Reduces initial bundle size by deferring OTEL loading
 */

import type { 
  TracerProvider,
  Tracer,
  Span,
  SpanOptions,
  Context,
  Attributes
} from '@opentelemetry/api'

interface OTelModule {
  TracerProvider?: any
  WebTracerProvider?: any
  SimpleSpanProcessor?: any
  BatchSpanProcessor?: any
  Resource?: any
  registerInstrumentations?: any
}

class LazyOTelLoader {
  private static instance: LazyOTelLoader
  private loadedModules = new Map<string, any>()
  private loadingPromises = new Map<string, Promise<any>>()
  private tracer: Tracer | null = null
  private tracerProvider: TracerProvider | null = null
  private isInitialized = false

  private constructor() {}

  static getInstance(): LazyOTelLoader {
    if (!LazyOTelLoader.instance) {
      LazyOTelLoader.instance = new LazyOTelLoader()
    }
    return LazyOTelLoader.instance
  }

  /**
   * Load core OpenTelemetry SDK on demand
   */
  async loadCore(): Promise<OTelModule> {
    if (this.loadedModules.has('core')) {
      return this.loadedModules.get('core')
    }

    if (this.loadingPromises.has('core')) {
      return this.loadingPromises.get('core')
    }

    const loadPromise = Promise.all([
      import('@opentelemetry/sdk-trace-web'),
      import('@opentelemetry/resources'),
      import('@opentelemetry/api'),
    ]).then(([traceModule, resourceModule, apiModule]) => {
      const module = {
        ...traceModule,
        ...resourceModule,
        ...apiModule,
      }
      this.loadedModules.set('core', module)
      return module
    })

    this.loadingPromises.set('core', loadPromise)
    return loadPromise
  }

  /**
   * Load instrumentation packages on demand
   */
  async loadInstrumentation(type: 'fetch' | 'xhr' | 'document' | 'user-interaction'): Promise<any> {
    const key = `instrumentation-${type}`
    
    if (this.loadedModules.has(key)) {
      return this.loadedModules.get(key)
    }

    if (this.loadingPromises.has(key)) {
      return this.loadingPromises.get(key)
    }

    let loadPromise: Promise<any>

    switch (type) {
      case 'fetch':
        loadPromise = import('@opentelemetry/instrumentation-fetch')
        break
      case 'xhr':
        loadPromise = import('@opentelemetry/instrumentation-xml-http-request')
        break
      case 'document':
        loadPromise = import('@opentelemetry/instrumentation-document-load')
        break
      case 'user-interaction':
        loadPromise = import('@opentelemetry/instrumentation-user-interaction')
        break
      default:
        throw new Error(`Unknown instrumentation type: ${type}`)
    }

    const finalPromise = loadPromise.then(module => {
      this.loadedModules.set(key, module)
      return module
    })

    this.loadingPromises.set(key, finalPromise)
    return finalPromise
  }

  /**
   * Load exporters on demand
   */
  async loadExporter(type: 'trace' | 'metrics'): Promise<any> {
    const key = `exporter-${type}`
    
    if (this.loadedModules.has(key)) {
      return this.loadedModules.get(key)
    }

    if (this.loadingPromises.has(key)) {
      return this.loadingPromises.get(key)
    }

    let loadPromise: Promise<any>

    switch (type) {
      case 'trace':
        loadPromise = import('@opentelemetry/exporter-trace-otlp-http')
        break
      case 'metrics':
        loadPromise = import('@opentelemetry/exporter-metrics-otlp-http')
        break
      default:
        throw new Error(`Unknown exporter type: ${type}`)
    }

    const finalPromise = loadPromise.then(module => {
      this.loadedModules.set(key, module)
      return module
    })

    this.loadingPromises.set(key, finalPromise)
    return finalPromise
  }

  /**
   * Initialize OpenTelemetry with lazy loading
   */
  async initialize(config: {
    serviceName: string
    serviceVersion?: string
    backendUrl?: string
    enableInstrumentation?: boolean
  }): Promise<void> {
    if (this.isInitialized) {
      return
    }

    // Load core modules
    const coreModule = await this.loadCore()
    
    // Create tracer provider
    const { WebTracerProvider } = coreModule
    const { Resource } = coreModule
    const { SemanticResourceAttributes } = await import('@opentelemetry/semantic-conventions')

    const resource = Resource.default({
      [SemanticResourceAttributes.SERVICE_NAME]: config.serviceName,
      [SemanticResourceAttributes.SERVICE_VERSION]: config.serviceVersion || 'unknown',
    })

    this.tracerProvider = new WebTracerProvider({
      resource,
    })

    // Load and configure exporter if backend URL is provided
    if (config.backendUrl) {
      const exporterModule = await this.loadExporter('trace')
      const { OTLPTraceExporter } = exporterModule
      
      const exporter = new OTLPTraceExporter({
        url: `${config.backendUrl}/v1/traces`,
      })

      const { BatchSpanProcessor } = coreModule
      this.tracerProvider.addSpanProcessor(new BatchSpanProcessor(exporter))
    }

    this.tracerProvider.register()
    this.tracer = this.tracerProvider.getTracer(config.serviceName)

    // Load instrumentation if enabled
    if (config.enableInstrumentation) {
      this.loadInstrumentationAsync()
    }

    this.isInitialized = true
  }

  /**
   * Load instrumentation asynchronously after initialization
   */
  private async loadInstrumentationAsync(): Promise<void> {
    // Load instrumentations with delay to not block initial load
    setTimeout(async () => {
      const { registerInstrumentations } = await import('@opentelemetry/instrumentation')
      
      // Load instrumentations one by one with delays
      const instrumentations = []

      // Load fetch instrumentation
      try {
        const fetchModule = await this.loadInstrumentation('fetch')
        instrumentations.push(new fetchModule.FetchInstrumentation())
      } catch (e) {
        console.debug('Failed to load fetch instrumentation:', e)
      }

      // Load XHR instrumentation after a delay
      setTimeout(async () => {
        try {
          const xhrModule = await this.loadInstrumentation('xhr')
          instrumentations.push(new xhrModule.XMLHttpRequestInstrumentation())
        } catch (e) {
          console.debug('Failed to load XHR instrumentation:', e)
        }
      }, 1000)

      // Load document instrumentation after a delay
      setTimeout(async () => {
        try {
          const docModule = await this.loadInstrumentation('document')
          instrumentations.push(new docModule.DocumentLoadInstrumentation())
        } catch (e) {
          console.debug('Failed to load document instrumentation:', e)
        }
      }, 2000)

      // Register all loaded instrumentations
      if (instrumentations.length > 0) {
        registerInstrumentations({
          instrumentations,
        })
      }
    }, 500) // Initial delay before starting instrumentation loading
  }

  /**
   * Get a tracer instance (loads on demand if not initialized)
   */
  async getTracer(name?: string): Promise<Tracer> {
    if (!this.isInitialized) {
      await this.initialize({
        serviceName: name || 'default',
      })
    }
    return this.tracer!
  }

  /**
   * Create a span (loads OpenTelemetry on demand)
   */
  async startSpan(
    name: string,
    options?: SpanOptions,
    context?: Context
  ): Promise<Span> {
    const tracer = await this.getTracer()
    return tracer.startSpan(name, options, context)
  }

  /**
   * Check if OpenTelemetry is loaded
   */
  isLoaded(): boolean {
    return this.isInitialized
  }

  /**
   * Preload OpenTelemetry modules without initializing
   */
  async preload(): Promise<void> {
    // Preload core modules
    this.loadCore().catch(() => {
      // Silently fail preloading
    })
  }
}

// Export singleton instance
export const otelLoader = LazyOTelLoader.getInstance()

// Export convenience functions
export async function lazyStartSpan(
  name: string,
  options?: SpanOptions,
  context?: Context
): Promise<Span> {
  return otelLoader.startSpan(name, options, context)
}

export async function lazyGetTracer(name?: string): Promise<Tracer> {
  return otelLoader.getTracer(name)
}

export async function initializeOTel(config: {
  serviceName: string
  serviceVersion?: string
  backendUrl?: string
  enableInstrumentation?: boolean
}): Promise<void> {
  return otelLoader.initialize(config)
}

// Export function to check if OTEL is loaded
export function isOTelLoaded(): boolean {
  return otelLoader.isLoaded()
}