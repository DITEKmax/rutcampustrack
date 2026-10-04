/// <reference types="vite/client" />

// The extra HTML files are served by Vite in development only, never build inputs.
if (import.meta.env.DEV) {
  const [{ createApp }, { default: TodayReview }] = await Promise.all([
    import('vue'),
    import('./TodayReview.vue'),
    import('../styles/base.pcss'),
  ])
  createApp(TodayReview).mount('#app')
} else {
  document.getElementById('app')?.replaceChildren()
}