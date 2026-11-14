import type { Component } from 'vue'
import type { Topic } from '../topics'

const loaders = {
  tabs: { topic: 'data', load: () => import('../demo/DemoTabs.vue') },
  lessonTransfer: { topic: 'schedule', load: () => import('../demo/LessonTransferScenario.vue') },
  studentTransfer: { topic: 'academic', load: () => import('../demo/StudentTransferScenario.vue') },
  campus: { topic: 'campus', load: () => import('../demo/CampusVersionsScenario.vue') },
  calculator: { topic: 'reports', load: () => import('../demo/AttendanceCalculatorScenario.vue') },
  rank: { topic: 'reports', load: () => import('../demo/StudentRankScenario.vue') },
  threshold: { topic: 'reports', load: () => import('../demo/JournalThresholdScenario.vue') },
  journalExport: { topic: 'reports', load: () => import('../demo/JournalExportScenario.vue') },
  outbox: { topic: 'reliability', load: () => import('../demo/OutboxRecoveryScenario.vue') },
  homework: { topic: 'notifications', load: () => import('../demo/HomeworkNotificationScenario.vue') },
} as const satisfies Record<string, { topic: Topic['slug']; load: () => Promise<{ default: Component }> }>

export type TopicScenarios = Partial<Record<keyof typeof loaders, Component>>

export async function loadTopicScenarios(topic: Topic): Promise<TopicScenarios> {
  const entries = Object.entries(loaders).filter(([, loader]) => loader.topic === topic.slug)
  return Object.fromEntries(await Promise.all(entries.map(async ([key, loader]) => [key, (await loader.load()).default])))
}
