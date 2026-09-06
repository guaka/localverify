// Use the locked repository dependency and disable CLI telemetry for every invocation.
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const result = spawnSync(process.execPath,
  [root + 'node_modules/@fission-ai/openspec/bin/openspec.js', ...process.argv.slice(2)],
  { cwd: root, stdio: 'inherit', env: { ...process.env, OPENSPEC_TELEMETRY: '0', DO_NOT_TRACK: '1' } });
if (result.error) console.error(result.error.message);
process.exit(result.status ?? 1);
