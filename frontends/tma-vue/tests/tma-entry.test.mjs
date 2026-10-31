import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import compilerDom from '@vue/compiler-dom'

const { NodeTypes, parse } = compilerDom
function elements(node) {
  return (node.children ?? []).flatMap((child) =>
    child.type === NodeTypes.ELEMENT ? [child, ...elements(child)] : [],
  )
}
function attribute(node, name) {
  return node.props.find(
    (prop) => prop.type === NodeTypes.ATTRIBUTE && prop.name.toLowerCase() === name,
  )?.value?.content
}

test('loads the official Telegram WebApp SDK in head before the app module', async () => {
  const indexPath = fileURLToPath(new URL('../index.html', import.meta.url))
  const html = await readFile(indexPath, 'utf8')
  const nodes = elements(parse(html))
  const head = nodes.find((node) => node.tag.toLowerCase() === 'head')
  assert.ok(head, 'index.html must contain a head section')
  const sdkUrl = 'https://telegram.org/js/telegram-web-app.js'
  const sdk = elements(head).find((node) => {
    const src = attribute(node, 'src')
    return node.tag.toLowerCase() === 'script' && (src === sdkUrl || src?.startsWith(sdkUrl + '?'))
  })
  assert.ok(sdk, 'the official Telegram WebApp SDK must be loaded from telegram.org in head')

  const sdkPosition = nodes.indexOf(sdk)
  const appModulePosition = nodes.findIndex(
    (node) =>
      node.tag.toLowerCase() === 'script' &&
      attribute(node, 'type') === 'module' &&
      attribute(node, 'src') === '/src/main.ts',
  )
  assert.notEqual(sdkPosition, -1, 'SDK script must be present')
  assert.notEqual(appModulePosition, -1, 'app module must be present')
  assert.ok(sdkPosition < appModulePosition, 'SDK script must precede the app module')
})
