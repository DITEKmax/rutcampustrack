/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { VueQueryPlugin, QueryClient }, { default: HomeworkReview }] = await Promise.all([
    import('vue'), import('@tanstack/vue-query'), import('./HomeworkReview.vue'), import('../styles/base.pcss'),
  ])
  createApp(HomeworkReview).use(VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } }) }).mount('#app')
} else {
  document.getElementById('app')?.replaceChildren()
}
