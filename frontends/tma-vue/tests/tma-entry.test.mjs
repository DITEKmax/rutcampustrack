import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

test('loads the official Telegram WebApp SDK in head before the app module', async () => {
  const indexPath = fileURLToPath(new URL('../index.html', import.meta.url))
  const html = await readFile(indexPath, 'utf8')
  const head = html.match(/<head\b[^>]*>([\s\S]*?)<\/head>/i)?.[1]
  assert.ok(head, 'index.html must contain a head section')
  assert.match(
    head,
    /<script\b[^>]*src=["']https:\/\/telegram\.org\/js\/telegram-web-app\.js(?:\?[^"']*)?["'][^>]*><\/script>/i,
    'the official Telegram WebApp SDK must be loaded from telegram.org in head',
  )

  const sdkPosition = html.indexOf('https://telegram.org/js/telegram-web-app.js')
  const appModulePosition = html.indexOf('<script type="module" src="/src/main.ts"></script>')
  assert.notEqual(sdkPosition, -1, 'SDK script must be present')
  assert.notEqual(appModulePosition, -1, 'app module must be present')
  assert.ok(sdkPosition < appModulePosition, 'SDK script must precede the app module')
})
