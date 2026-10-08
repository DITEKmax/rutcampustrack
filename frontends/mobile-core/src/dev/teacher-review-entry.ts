/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { default: TeacherReview }] = await Promise.all([import('vue'), import('./TeacherReview.vue'), import('../styles/base.pcss')])
  createApp(TeacherReview).mount('#app')
} else { document.getElementById('app')?.replaceChildren() }
