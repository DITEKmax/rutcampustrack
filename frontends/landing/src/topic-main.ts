import { createApp } from 'vue'
import TopicApp from './TopicApp.vue'
import { loadTopic } from './story/topic-chapters'
import { loadTopicScenarios } from './story/topic-scenarios'
import { presentationHome, topics } from './topics'
import './landing.pcss'
import './topics.pcss'

const app = document.querySelector<HTMLElement>('#app')!
const topic = topics.find(item => item.slug === app.dataset.topic)

if (topic) {
  const reloadKey = 'rct.landing.chunk-reload:' + location.pathname
  window.addEventListener('vite:preloadError', () => {
    try {
      if (sessionStorage.getItem(reloadKey)) return
      sessionStorage.setItem(reloadKey, '1')
    } catch {
      // Keep the generated HTML available when storage is disabled.
      return
    }
    location.reload()
  })

  Promise.all([loadTopic(topic), loadTopicScenarios(topic)]).then(([chapters, scenarios]) => {
    createApp(TopicApp, { topic, chapters, scenarios }).mount(app)
  }).catch(() => {
    const link = document.createElement('a')
    link.href = presentationHome + '#topics'
    link.textContent = 'Интерактивная часть недоступна. Вернуться ко всем темам'
    link.className = 'landing-shell topic-load-error'
    app.append(link)
  })
}
