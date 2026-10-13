const fs = require('node:fs');
const path = require('node:path');
const ts = require('/workspace/rutcampustrack/frontends/node_modules/typescript/lib/typescript.js');
const config = path.join(__dirname, 'tsconfig.ci-first3.json');
if (ts.version !== '5.9.3') throw new Error('Pinned TypeScript version required');
const raw = ts.readConfigFile(config, ts.sys.readFile);
const parsed = ts.parseJsonConfigFileContent(raw.config ?? {}, ts.sys, __dirname, undefined, config);
const program = ts.createProgram(parsed.fileNames, parsed.options);
const diagnostics = [...(raw.error ? [raw.error] : []), ...parsed.errors, ...ts.getPreEmitDiagnostics(program)];
const errors = diagnostics.filter((item) => item.category === ts.DiagnosticCategory.Error);
const output = process.env.RCT_FOCUS3_TYPES_OUTPUT;
if (output !== path.join(process.env.RCT_FOCUS3_RUN ?? '', 'supervision', 'types.safe.json')) {
  throw new Error('Source typecheck requires its private safe output');
}
const value = { phase: 'SCOPED_TYPES_ONLY', typescript_version: ts.version,
  error_count: errors.length, diagnostic_codes: [...new Set(errors.map((item) => item.code))].sort((a, b) => a - b),
  root_file_count: parsed.fileNames.length, pass: errors.length === 0 };
fs.writeFileSync(output, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
process.exit(value.pass ? 0 : 1);
