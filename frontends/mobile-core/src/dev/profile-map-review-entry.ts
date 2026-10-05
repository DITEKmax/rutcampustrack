/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: ProfileMapReview }] = await Promise.all([import('vue'), import('./ProfileMapReview.vue'), import('../styles/base.pcss')])
  createApp(ProfileMapReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
