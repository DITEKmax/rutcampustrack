/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: HeadmanReview }] = await Promise.all([import('vue'), import('./HeadmanReview.vue'), import('../styles/base.pcss')])
  createApp(HeadmanReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
