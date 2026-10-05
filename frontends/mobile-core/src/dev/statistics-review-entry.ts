/// <reference types="vite/client" />
if (import.meta.env.DEV) {
  const [{ createApp }, { VueQueryPlugin, QueryClient }, { default: StatisticsReview }] = await Promise.all([
    import('vue'), import('@tanstack/vue-query'), import('./StatisticsReview.vue'), import('../styles/base.pcss'),
  ])
  createApp(StatisticsReview).use(VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } }) }).mount('#app')
} else {
  document.getElementById('app')?.replaceChildren()
}
