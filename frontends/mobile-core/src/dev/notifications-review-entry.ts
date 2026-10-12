/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: NotificationsReview }] = await Promise.all([
    import('vue'),
    import('./NotificationsReview.vue'),
    import('../styles/base.pcss'),
  ])
  createApp(NotificationsReview).mount('#app')
} else {
  document.getElementById('app')?.replaceChildren()
}
