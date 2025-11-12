// vite.config.minimal.ts
import { resolve as resolvePath } from 'path'
import { defineConfig } from 'vite'

export default defineConfig({
  build: {
    target: 'es2020',
    lib: {
      formats: ['es', 'cjs'],
      entry: resolvePath(__dirname, 'src/index-minimal.ts'),
      fileName: (format) => `index-minimal.${format === 'es' ? 'js' : 'cjs'}`,
    },
    minify: 'terser',
    terserOptions: {
      ecma: 2020,
      module: true,
      toplevel: true,
      compress: {
        ecma: 2020,
        module: true,
        toplevel: true,
        unsafe_arrows: true,
        drop_console: true,
        drop_debugger: true,
        passes: 3,
        pure_getters: true,
        unsafe: true,
        unsafe_comps: true,
        unsafe_math: true,
        unsafe_methods: true,
        unsafe_proto: true,
        unsafe_regexp: true,
        unsafe_undefined: true,
        unused: true,
        dead_code: true,
        inline: 3,
        side_effects: false,
      },
      mangle: {
        toplevel: true,
      },
      format: {
        comments: false,
        ecma: 2020,
      },
    },
    rollupOptions: {
      external: ['highlight.run'],
      treeshake: {
        moduleSideEffects: false,
        propertyReadSideEffects: false,
        tryCatchDeoptimization: false,
        unknownGlobalSideEffects: false,
      },
      output: {
        compact: true,
        minifyInternalExports: true,
      },
    },
  },
})