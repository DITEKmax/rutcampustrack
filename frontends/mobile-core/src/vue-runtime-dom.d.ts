import '@vue/runtime-dom'

declare module '@vue/runtime-dom' {
  interface ButtonHTMLAttributes {
    onClick?: ((payload: PointerEvent) => void) | undefined
  }
}
