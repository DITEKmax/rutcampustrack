/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: HeadmanStatsReview }] = await Promise.all([import('vue'), import('./HeadmanStatsReview.vue'), import('../styles/base.pcss')])
  createApp(HeadmanStatsReview).mount('#app')
} else document.getElementById('app')?.replaceChildren()
