import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from '../../frontend/node_modules/typescript/lib/typescript.js';

// Node 20 cannot load TypeScript directly. Transpile pure modules using the project's
// existing compiler; Angular components remain covered by the real browser tests.
export function loadTypeScript(file, cache = new Map()) {
  const filename = file instanceof URL ? fileURLToPath(file) : path.resolve(file);
  if (cache.has(filename)) return cache.get(filename).exports;
  const module = { exports: {} };
  cache.set(filename, module);
  const nativeRequire = createRequire(filename);
  const require = (specifier) =>
    specifier.startsWith('.')
      ? loadTypeScript(path.resolve(path.dirname(filename), specifier) + '.ts', cache)
      : nativeRequire(specifier);
  const compiled = ts.transpileModule(readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: filename,
  }).outputText;
  new Function('module', 'exports', 'require', compiled)(module, module.exports, require);
  return module.exports;
}
