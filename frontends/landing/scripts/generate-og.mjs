// Regenerate the legacy social-image URL from its branded vector.
// The current metadata uses the original PNG export in dist/assets/brand-20b-g08/.

import { Resvg } from '@resvg/resvg-js'
import { readFileSync, writeFileSync } from 'node:fs'

const svgPath = new URL('../dist/assets/og/ruttrack-og.svg', import.meta.url)
const pngPath = new URL('../dist/assets/og/ruttrack-og-1200x630.png', import.meta.url)
const svg = readFileSync(svgPath, 'utf8')
const image = new Resvg(svg, { font: { loadSystemFonts: false } }).render().asPng()
writeFileSync(pngPath, image)
