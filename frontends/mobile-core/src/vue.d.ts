declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, never>, Record<string, never>, never>
  export default component
}

declare module '*.svg' {
  const source: string
  export default source
}