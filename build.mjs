// Bundles the plugin from the tsc-emitted ESM (dist/esm) into the two
// distributables the Capacitor tooling expects:
//   - dist/plugin.js       IIFE, loaded into the WebView global scope
//   - dist/plugin.cjs.js   CommonJS, for bundler/Node consumers
//
// Replaces the historical rollup step. We bundle from dist/esm/index.js (the
// tsc output) rather than src so esbuild and tsc can't diverge on transpile.
import esbuild from 'esbuild';

const GLOBAL_NAME = 'capacitorBackgroundGeolocation';

// In the IIFE build there is no module system: `@capacitor/core` must resolve
// to the `capacitorExports` object Capacitor injects into the global scope.
// (rollup did this via output.globals; esbuild needs a resolver plugin.)
const capacitorCoreGlobal = {
  name: 'capacitor-core-global',
  setup(build) {
    build.onResolve({ filter: /^@capacitor\/core$/ }, () => ({
      path: '@capacitor/core',
      namespace: 'capacitor-core-global',
    }));
    build.onLoad({ filter: /.*/, namespace: 'capacitor-core-global' }, () => ({
      contents: 'module.exports = globalThis.capacitorExports;',
      loader: 'js',
    }));
  },
};

const base = {
  entryPoints: ['dist/esm/index.js'],
  bundle: true,
  sourcemap: true,
};

await esbuild.build({
  ...base,
  format: 'iife',
  globalName: GLOBAL_NAME,
  outfile: 'dist/plugin.js',
  plugins: [capacitorCoreGlobal],
});

await esbuild.build({
  ...base,
  format: 'cjs',
  outfile: 'dist/plugin.cjs.js',
  external: ['@capacitor/core'],
});
