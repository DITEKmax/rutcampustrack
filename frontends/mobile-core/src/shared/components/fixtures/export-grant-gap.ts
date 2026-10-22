import { defineComponent, h, type Component } from 'vue'

/** Test-only equivalent broken admission: scope changes work, grant loss never reaches the real control. */
export function createExportGrantGapFixture(actual: Component): Component {
  return defineComponent({
    name: 'ExportGrantGapFixture',
    inheritAttrs: false,
    props: {
      port: { type: Object, required: true },
      formats: { type: Function, required: true },
      request: { type: Function, required: true },
      scopeKey: { type: String, required: true },
      disabled: Boolean,
    },
    setup(props, { attrs }) {
      return () => h(actual, { ...attrs, ...props, disabled: false })
    },
  })
}
