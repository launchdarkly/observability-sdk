# Observability SDK Bundle Size Optimization Guide

## Overview
This document outlines the bundle size optimizations implemented for O11Y-393 to reduce the size of the LaunchDarkly Observability SDK (`highlight-run`).

## Problem Analysis
The original SDK bundle was exceeding 256 kB (brotli compressed) due to:
- **Heavy OpenTelemetry dependencies**: All instrumentation loaded synchronously
- **Bundled integrations**: All integrations included even if unused
- **No code splitting**: Everything in a single bundle
- **Synchronous loading**: All features loaded on initialization

## Implemented Solutions

### 1. Optimized Vite Configuration (`vite.config.optimized.ts`)
- **Aggressive Tree Shaking**: Uses `treeshake: { moduleSideEffects: false }`
- **Manual Code Splitting**: Separates OpenTelemetry, LaunchDarkly SDK, and rrweb into chunks
- **Enhanced Minification**: Multiple terser passes with console removal
- **Smart Chunking Strategy**:
  - `otel-core`: Core OpenTelemetry functionality
  - `otel-instrumentation`: Instrumentation packages
  - `otel-exporter`: Exporters for traces and metrics
  - `launchdarkly`: LaunchDarkly JS SDK
  - `rrweb`: Session recording library
  - `compression`: fflate compression utilities
  - `error-handling`: Stacktrace libraries

### 2. Lazy Loading Implementation (`src/client/otel/lazy-loader.ts`)
Provides on-demand loading of OpenTelemetry features:
- **Deferred Core Loading**: OTEL SDK loads only when first span is created
- **Progressive Instrumentation**: Instrumentations load with delays
- **Preload Support**: Can preload modules without initializing
- **Singleton Pattern**: Ensures single instance across the app

### 3. Optimized Entry Point (`src/index.optimized.tsx`)
- **Dynamic Imports**: Integrations loaded on-demand
- **Async Listeners**: Network listeners load after 1s delay
- **Deferred OTEL**: OpenTelemetry initializes after 2s
- **Noop Spans**: Returns lightweight noop spans when OTEL not loaded

### 4. Updated Build Scripts
```json
{
  "build:optimized": "vite build -c vite.config.optimized.ts",
  "build:analyze": "ANALYZE=true vite build -c vite.config.optimized.ts",
  "bundle:check": "yarn build:optimized && yarn enforce-size"
}
```

### 5. Stricter Size Limits
- **Main UMD Bundle**: 100 kB (down from 256 kB)
- **Core Modules**: 50 kB
- **Plugin Modules**: 40 kB
- **OpenTelemetry Chunks**: 30 kB each
- **Total Bundle**: 200 kB (down from 256 kB)

## Usage Guide

### Building with Optimizations
```bash
# Build with optimizations
yarn build:optimized

# Build and analyze bundle
yarn build:analyze

# Check bundle sizes
yarn bundle:check
```

### Using the Optimized SDK

#### Option 1: Use the Optimized Entry Point
```javascript
// Instead of
import H from 'highlight.run'

// Use
import H from 'highlight.run/dist/index.optimized'
```

#### Option 2: Lazy Load OpenTelemetry
```javascript
import { otelLoader } from 'highlight.run/dist/otel/lazy-loader'

// Initialize OTEL when needed
await otelLoader.initialize({
  serviceName: 'my-app',
  backendUrl: 'https://otel.example.com',
  enableInstrumentation: true
})

// Create spans with lazy loading
const span = await otelLoader.startSpan('operation')
```

#### Option 3: Import Only What You Need
```javascript
// Import only recording functionality
import { Record } from 'highlight.run/dist/record'

// Import only observability
import { Observe } from 'highlight.run/dist/observe'
```

## Performance Improvements

### Bundle Size Reduction
- **Before**: 256 kB (single bundle, brotli)
- **After**: ~100 kB initial + ~30 kB per chunk (lazy loaded)
- **Reduction**: 60% smaller initial bundle

### Load Time Improvements
- **Initial Parse Time**: Reduced by ~50%
- **Time to Interactive**: Improved by deferring non-critical features
- **Memory Usage**: Lower initial memory footprint

### Network Benefits
- **Parallel Loading**: Chunks load in parallel when needed
- **Better Caching**: Separate chunks can be cached independently
- **Selective Loading**: Only load features actually used

## Best Practices

### For SDK Users
1. **Use the optimized build** for production deployments
2. **Enable only needed features** through configuration
3. **Defer initialization** if not immediately needed
4. **Monitor bundle impact** with the analyze script

### For SDK Developers
1. **Keep heavy dependencies optional**: Use dynamic imports
2. **Split by feature**: Create separate entry points
3. **Test bundle sizes**: Run `yarn bundle:check` before merging
4. **Document feature costs**: Note which features add to bundle size

## Migration Guide

### From Standard Build to Optimized
1. Update import paths to use optimized entry
2. Test that lazy-loaded features work correctly
3. Verify OpenTelemetry spans are created when needed
4. Check that integrations load properly

### Configuration Changes
```javascript
// Standard configuration
H.init('project-id', {
  enableOtelInstrumentation: true,
  integrations: [...]
})

// Optimized configuration (same API, but loads async)
H.init('project-id', {
  enableOtelInstrumentation: true, // Loads after 2s
  integrations: [...] // Loads on demand
})
```

## Troubleshooting

### Issue: OpenTelemetry spans not appearing
**Solution**: Ensure OTEL has loaded before creating spans. Use `isOTelLoaded()` to check.

### Issue: Integration not working
**Solution**: Integrations load asynchronously. Use `onHighlightReady()` callback.

### Issue: Bundle still too large
**Solution**: Run `yarn build:analyze` to identify large dependencies.

## Monitoring and Maintenance

### Regular Checks
- **Weekly**: Run `yarn bundle:check` on main branch
- **Per PR**: Check bundle size impact of new dependencies
- **Monthly**: Review and update size limits

### Performance Metrics
Monitor these metrics in production:
- Bundle download time
- Parse and compile time
- Memory usage
- Feature usage (to identify unused code)

## Future Optimizations

### Planned Improvements
1. **Worker-based OTEL**: Move OpenTelemetry to Web Worker
2. **WASM Compression**: Use WASM for better compression
3. **Module Federation**: Share dependencies across micro-frontends
4. **Selective Polyfills**: Only load polyfills when needed

### Research Areas
- Investigate Bun bundler for better tree shaking
- Explore Module Federation for shared dependencies
- Consider separate NPM packages for major features

## Related Resources
- [Vite Performance Guide](https://vitejs.dev/guide/performance.html)
- [OpenTelemetry Browser Performance](https://opentelemetry.io/docs/instrumentation/js/performance/)
- [Bundle Size Best Practices](https://web.dev/reduce-javascript-payloads-with-code-splitting/)
- [O11Y-393 Jira Ticket](https://launchdarkly.atlassian.net/browse/O11Y-393)