/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: RequestsReview }] = await Promise.all([import('vue'), import('./RequestsReview.vue'), import('../styles/base.pcss')])
  createApp(RequestsReview).mount('#app')
} else document.getElementById('app')?.replaceChildren()
