/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{createApp},{default:AdminUsersReview}] = await Promise.all([import('vue'),import('./AdminUsersReview.vue'),import('../styles/base.pcss')])
  createApp(AdminUsersReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
