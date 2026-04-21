// Generate src/app/api/generated/{service}.types.ts from backend /api-docs.
// M07 Группа 3 (QC2) — типы для всех 4 REST-сервисов, нужных web-panel:
//   auth, academic, schedule, attendance. Gateway — proxy-only.
//
// Usage:
//   node scripts/generate-types.mjs                # fetch from localhost
//   node scripts/generate-types.mjs --offline      # re-run парсер на
//                                                  #   committed JSON
//
// Backend должен быть запущен (docker-compose + gradle bootRun).
// scripts/m07-g3-launch-services.sh в корне репо поднимает всё.
//
// Spec'ы committed в docs/openapi/{service}.json для CI drift-guard (NEW-84).

import { writeFile, mkdir, readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '../../..');
const webPanelRoot = resolve(__dirname, '..');

const offline = process.argv.includes('--offline');

const services = [
  { name: 'auth',       port: 9090 },
  { name: 'academic',   port: 9091 },
  { name: 'schedule',   port: 9092 },
  { name: 'attendance', port: 9093 },
];

const openapiDir = resolve(repoRoot, 'docs/openapi');
const genDir = resolve(webPanelRoot, 'src/app/api/generated');

await mkdir(openapiDir, { recursive: true });
await mkdir(genDir, { recursive: true });

for (const svc of services) {
  const jsonPath = resolve(openapiDir, `${svc.name}.json`);
  let spec;

  if (offline) {
    if (!existsSync(jsonPath)) {
      console.error(`[generate-types] ${svc.name}: offline mode требует ${jsonPath} — запусти без --offline сначала`);
      process.exit(1);
    }
    spec = JSON.parse(await readFile(jsonPath, 'utf8'));
    console.log(`[generate-types] ${svc.name}: offline (${jsonPath})`);
  } else {
    const url = `http://localhost:${svc.port}/api-docs`;
    console.log(`[generate-types] ${svc.name}: fetching ${url}`);
    const res = await fetch(url);
    if (!res.ok) {
      console.error(`[generate-types] ${svc.name}: HTTP ${res.status} from ${url}`);
      process.exit(1);
    }
    spec = await res.json();
    const pretty = JSON.stringify(spec, null, 2);
    // Не перезаписываем JSON если PWA уже записал его (single source of truth
    // в docs/openapi/) — только если файла нет, или размер отличается.
    if (!existsSync(jsonPath) || (await readFile(jsonPath, 'utf8')).length !== pretty.length + 1) {
      await writeFile(jsonPath, pretty + '\n');
      console.log(`[generate-types] ${svc.name}: saved ${jsonPath} (${pretty.length} B)`);
    } else {
      console.log(`[generate-types] ${svc.name}: ${jsonPath} up-to-date`);
    }
  }

  const ast = await openapiTS(spec, {
    immutable: false,
  });
  const ts = astToString(ast);
  const tsPath = resolve(genDir, `${svc.name}.types.ts`);
  const header = `// AUTO-GENERATED — do not edit by hand.\n// Regenerate: npm run generate:types (requires backend running).\n// Source: docs/openapi/${svc.name}.json (committed for CI drift-guard).\n\n`;
  await writeFile(tsPath, header + ts);
  console.log(`[generate-types] ${svc.name}: wrote ${tsPath} (${(header + ts).length} B)`);
}

console.log('[generate-types] done');
