import { createTheme, createThemePreference } from '../../../shared/foundation'
import { shallowRef } from 'vue'

const preference = createThemePreference(() => localStorage)
const controller = createTheme({ mode: preference.read() ?? 'system' })
export const theme = shallowRef({ mode: controller.mode, resolvedMode: controller.resolvedMode })
controller.subscribe((snapshot) => {
  theme.value = snapshot
})
preference.subscribe((mode) => controller.setMode(mode ?? 'system'))
export function setTheme(value: string) {
  if (value !== 'light' && value !== 'dark' && value !== 'system') return
  preference.write(value)
  controller.setMode(value)
}
