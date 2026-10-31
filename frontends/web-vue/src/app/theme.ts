import {
  createDeviceThemeMirror,
  createDeviceThemeTransport,
  createTheme,
  createThemePreference,
} from '../../../shared/foundation'
import { shallowRef, watch } from 'vue'
import { webSession } from '../auth/session'

const preference = createThemePreference(() => localStorage)
const controller = createTheme({ mode: preference.read() ?? 'system' })
export const theme = shallowRef({ mode: controller.mode, resolvedMode: controller.resolvedMode })
controller.subscribe((snapshot) => {
  theme.value = snapshot
})
const mirror = createDeviceThemeMirror({
  theme: controller,
  preference,
  transport: createDeviceThemeTransport(),
})
watch(
  () => {
    const session = webSession.snapshot.value
    const accessToken = webSession.accessToken()
    return session && accessToken
      ? {
          key: `${webSession.generation.value}:${session.userId}:${session.sessionId}`,
          accessToken,
        }
      : null
  },
  (session) => mirror.setSession(session),
  { immediate: true },
)
window.addEventListener('online', () => mirror.reconcile())
export function setTheme(value: string) {
  if (value !== 'light' && value !== 'dark' && value !== 'system') return
  controller.setMode(value)
}
