import js from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/@eslint/js/src/index.js'
import pluginVue from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/eslint-plugin-vue/dist/index.js'
import globals from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/globals/index.js'
import tseslint from 'file:///C:/Users/maksd/.codex/worktrees/d650/rutcampustrack/frontends/node_modules/typescript-eslint/dist/index.js'

export default tseslint.config(
  { ignores: ['**/dist/**'] },
  {
    ...js.configs.recommended,
    languageOptions: {
      globals: { ...globals.browser, ...globals.node },
    },
  },
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser },
    },
  },
)
