import { startLanding } from './start-landing'
import { loadTopic } from './story/topic-chapters'
import { topics } from './topics'

void startLanding(async root => {
  const topic = topics.find(item => item.slug === root.dataset.topic)
  if (!topic) throw new Error('Unknown landing topic')

  const [{ createTopicApp }, chapters, report] = await Promise.all([
    import('./topic-app'),
    loadTopic(topic),
    topic.slug === 'design' ? import('./design/DesignReport.vue') : undefined,
  ])

  return createTopicApp(topic, chapters, report?.default)
})
