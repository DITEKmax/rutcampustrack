// Generate PWA icons from public/favicon.svg.
// Outputs:
//   public/icons/icon-192.png            (any, edge-to-edge logo)
//   public/icons/icon-512.png            (any, edge-to-edge logo)
//   public/icons/icon-192-maskable.png   (maskable, logo within 80% safe zone)
//   public/icons/icon-512-maskable.png   (maskable, logo within 80% safe zone)
//   public/icons/apple-touch-icon.png    (180x180, opaque background, no transparency)
//
// Maskable variants embed the SVG into an opaque background scaled to 80%
// so Android's adaptive-icon mask never clips the logo. See
// https://web.dev/articles/maskable-icon.

import sharp from 'sharp'
import { readFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const svgPath = resolve(root, 'public/favicon.svg')
const outDir = resolve(root, 'public/icons')
mkdirSync(outDir, { recursive: true })

const svg = readFileSync(svgPath)
const BG = '#0A0E17' // matches manifest background_color / theme_color

async function renderAny(size, outName) {
  await sharp(svg, { density: 384 })
    .resize(size, size, { fit: 'contain', background: BG })
    .flatten({ background: BG })
    .png({ compressionLevel: 9 })
    .toFile(resolve(outDir, outName))
}

async function renderMaskable(size, outName) {
  const inner = Math.round(size * 0.8)
  const offset = Math.round((size - inner) / 2)
  const logo = await sharp(svg, { density: 384 })
    .resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer()
  await sharp({
    create: { width: size, height: size, channels: 3, background: BG }
  })
    .composite([{ input: logo, top: offset, left: offset }])
    .png({ compressionLevel: 9 })
    .toFile(resolve(outDir, outName))
}

await Promise.all([
  renderAny(192, 'icon-192.png'),
  renderAny(512, 'icon-512.png'),
  renderMaskable(192, 'icon-192-maskable.png'),
  renderMaskable(512, 'icon-512-maskable.png'),
  renderAny(180, 'apple-touch-icon.png')
])

console.log('icons written to', outDir)
