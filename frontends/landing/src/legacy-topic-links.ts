import { topics, topicHref, type ChapterId } from './topics'

// Operation IDs normally share their chapter prefix. Older exceptions and demo anchors stay valid.
const legacyGroups: Readonly<Record<string, ChapterId>> = {
  'gateway': 'architecture',
  'auth': 'architecture',
  'gateway-routing': 'architecture',
  'correlation': 'architecture',
  'checkin': 'architecture',
  'clients': 'architecture',
  'academic': 'architecture',
  'schedule': 'architecture',
  'transaction': 'architecture',
  'notification': 'architecture',
  'events': 'architecture',
  'reports': 'architecture',
  'observability': 'architecture',
  'pattern-outbox': 'patterns',
  'pattern-outbox-publisher': 'patterns',
  'pattern-envelope': 'patterns',
  'pattern-consumer-dedup': 'patterns',
  'pattern-dlq': 'patterns',
  'pattern-http-replay': 'patterns',
  'pattern-concurrency': 'patterns',
  'pattern-coordinator': 'patterns',
  'pattern-integrations': 'patterns',
  'pattern-invalidation': 'patterns',
  'pattern-async-owner': 'patterns',
  'pattern-conditional-reads': 'patterns',
  'demo-tabs': 'transactions',
  'demo-lesson-transfer': 'lesson-changes',
  'demo-student-transfer': 'student-transfer-history',
  'demo-campus-versions': 'campus-map',
  'demo-attendance-calculator': 'attendance-statistics',
  'demo-student-ranking': 'student-ranking',
  'demo-journal-threshold': 'attendance-journal',
  'demo-journal-export': 'attendance-journal-export',
  'demo-outbox-recovery': 'resilience',
  'demo-homework-notifications': 'notification-actions',
}

const chapterTopics = topics.flatMap(topic => topic.chapters.map(chapter => ({ chapter, topic })))
  .sort((a, b) => b.chapter.length - a.chapter.length)

export function legacyTopicHref(hash: string): string | undefined {
  const id = hash.slice(1).replace(/(?:-json)?-title$/, '')
  if (!id) return undefined
  const group = Object.hasOwn(legacyGroups, id) ? legacyGroups[id] : undefined
  const owner = chapterTopics.find(({ chapter }) => group
    ? group === chapter
    : id === chapter || id.startsWith(chapter + '-'))
  return owner && topicHref(owner.topic, hash)
}
