import resolve from '@rollup/plugin-node-resolve';
import commonjs from '@rollup/plugin-commonjs';
import typescript from '@rollup/plugin-typescript';
import terser from '@rollup/plugin-terser';
import json from '@rollup/plugin-json';

export default {
  input: 'src/index.ts',
  output: [
    {
      file: 'dist/index.js',
      format: 'es',
      sourcemap: true,
    },
    {
      file: 'dist/index.cjs',
      format: 'cjs',
      sourcemap: true,
    },
  ],
  external: [
    '@launchdarkly/react-native-client-sdk',
    'react-native',
    'expo-constants',
    '@react-native-async-storage/async-storage',
    'react-native-device-info',
    'react-native-get-random-values',
    'uuid',
    'react-native-exception-handler',
    /@opentelemetry\/.*/,
  ],
  plugins: [
    json(),
    resolve({
      preferBuiltins: false,
      browser: true,
    }),
    commonjs(),
    typescript({
      tsconfig: './tsconfig.json',
      declaration: true,
      declarationDir: './dist',
    }),
    terser({
      compress: {
        drop_console: false,
      },
    }),
  ],
};
