/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_TMA_DEV: string
  readonly VITE_TMA_MOCK_USER: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Font packages ship CSS only — declare as modules so TypeScript accepts the import
declare module '@fontsource-variable/geist' {
  const content: never
  export default content
}
