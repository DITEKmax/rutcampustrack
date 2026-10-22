import { readFileSync, writeFileSync } from 'node:fs'
import { extname } from 'node:path'

const folder = '/workspace/scratch/rct-validation/format_freeze_prep/'
const source = readFileSync('/workspace/rutcampustrack/scripts/format-authored.mjs', 'utf8')
const pure = source.slice(source.indexOf('export function classify'), source.indexOf('\nconst inventory ='))
const classify = new Function('extname', pure.replace('export function', 'function') + '\nreturn classify;')(extname)
const data = JSON.parse(readFileSync(folder + 'expected-export-inputs.json', 'utf8'))
for (const row of data.files) {
  row.existing_classifier = classify(row.path)
  if (!row.format_tool && row.existing_classifier.tool) row.format_tool = row.existing_classifier.tool
  if (row.existing_classifier.reason) row.disposition = row.existing_classifier.reason
}
writeFileSync(folder + 'expected-export-inputs.json', JSON.stringify(data, null, 2) + '\n')
