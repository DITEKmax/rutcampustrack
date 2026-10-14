import { copyFileSync, mkdirSync, writeFileSync, chmodSync, existsSync } from 'node:fs'
import { isAbsolute, join } from 'node:path'

// Create a NEW directory only: no update/delete of arbitrary existing runtime or private stand.
const [directory, jar] = process.argv.slice(2)
const url = new URL(process.env.E2E_BASE_URL ?? 'https://localhost:8443')
if (
  process.env.RCT_E2E_SYNTHETIC_SEED !== 'true' ||
  url.protocol !== 'https:' ||
  !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) ||
  url.port !== '8443' ||
  !directory ||
  !isAbsolute(directory) ||
  !jar ||
  !isAbsolute(jar) ||
  !existsSync(jar)
)
  throw new Error(
    'Prepare clock requires explicit synthetic loopback:8443, new absolute directory and built fixture jar',
  )
const day = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Europe/Moscow',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).formatToParts(new Date())
const part = (name) => day.find((item) => item.type === name).value
let date = new Date(`${part('year')}-${part('month')}-${part('day')}T06:00:00Z`)
// Keep synthetic Today lessons ahead of real PostgreSQL receipt timestamps,
// including evening runs; educational clocks alone are fixed at 09:00 Moscow.
date = new Date(date.getTime() + 86400000)
while ([0, 6].includes(date.getUTCDay())) date = new Date(date.getTime() + 86400000)
mkdirSync(directory, { mode: 0o755 })
copyFileSync(jar, join(directory, 'clock-fixture.jar'))
chmodSync(join(directory, 'clock-fixture.jar'), 0o644)
writeFileSync(join(directory, 'disposable-target'), 'RCT_E2E_DISPOSABLE\n', { mode: 0o644 })
writeFileSync(join(directory, 'current-instant'), `${date.toISOString()}\n`, { mode: 0o644 })
process.stdout.write(
  `${JSON.stringify({ teachingInstant: date.toISOString(), timeZone: 'Europe/Moscow', fixtureDirectory: directory, target: 'SYNTHETIC_DISPOSABLE_ONLY' })}\n`,
)
