import { mkdirSync, writeFileSync } from 'node:fs'

const latest = process.env.VITE_APP_VERSION
  ?? process.env.npm_package_version
  ?? '0.0.0'

const minimumSupported = process.env.VITE_MIN_SUPPORTED_VERSION || latest
const force = (process.env.VITE_FORCE_UPDATE ?? 'true').toLowerCase() !== 'false'
const message = process.env.VITE_UPDATE_MESSAGE
  ?? 'Доступна новая версия RutTrack. Обновите приложение, чтобы продолжить.'

const publicDir = new URL('../public/', import.meta.url)
mkdirSync(publicDir, { recursive: true })

writeFileSync(
  new URL('version.json', publicDir),
  `${JSON.stringify({
    latest,
    minimumSupported,
    force,
    message,
  }, null, 2)}\n`,
  'utf8',
)
