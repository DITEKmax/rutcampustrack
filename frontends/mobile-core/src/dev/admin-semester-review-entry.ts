/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: Review }] = await Promise.all([import('vue'), import('./AdminSemesterReview.vue'), import('../styles/base.pcss')])
  createApp(Review).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
