import vue from '../../frontends/node_modules/@vitejs/plugin-vue/dist/index.mjs'

// These suites use their own Vue host renderer in Node, so compile the SFC
// client render functions instead of server-rendering templates.
const vuePlugin = vue()
const vueTransform = typeof vuePlugin.transform === 'function' ? vuePlugin.transform : vuePlugin.transform.handler
vuePlugin.transform = function (source, id, options) {
  return vueTransform.call(this, source, id, { ...options, ssr: false })
}
export default {
  plugins: [vuePlugin],
  css: { postcss: '../../frontends/postcss.config.mjs' },
  test: { environment: 'node' },
}
