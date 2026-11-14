import { createApp, type Component } from 'vue'
import TopicApp from './TopicApp.vue'
import type { LoadedChapter } from './story/topic-chapters'
import type { Topic } from './topics'
import './landing.pcss'
import './topics.pcss'

export function createTopicApp(topic: Topic, chapters: LoadedChapter[], report?: Component) {
  return createApp(TopicApp, { topic, chapters, report })
}
