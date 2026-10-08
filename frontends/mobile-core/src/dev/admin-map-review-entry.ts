/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: AdminMapReview }] = await Promise.all([import('vue'), import('./AdminMapReview.vue'), import('../styles/base.pcss')])
  createApp(AdminMapReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
