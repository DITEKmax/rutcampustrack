/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: AttendanceReview }] = await Promise.all([import('vue'), import('./AttendanceReview.vue'), import('../styles/base.pcss')])
  createApp(AttendanceReview).mount('#app')
} else {
  document.getElementById('app')?.replaceChildren()
}
