/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: AdminShellReview }] = await Promise.all([import('vue'), import('./AdminShellReview.vue'), import('../styles/base.pcss')])
  createApp(AdminShellReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
