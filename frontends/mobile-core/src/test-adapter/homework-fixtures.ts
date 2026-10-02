import completedHomework from '../../fixtures/homework-completion-completed.json'
import homeworkFeed from '../../fixtures/homework-feed.json'
import undoneHomework from '../../fixtures/homework-completion-undone.json'
import type { StudentHomework, StudentHomeworkCompletion } from '../api/types'

export const fixtureHomeworkFeed = {
  ...homeworkFeed,
  items: homeworkFeed.items.map((item) => ({ ...item, bindingMode: bindingMode(item.bindingMode) })),
} satisfies StudentHomework
export const fixtureCompletedHomework = completedHomework satisfies StudentHomeworkCompletion
export const fixtureUndoneHomework = undoneHomework satisfies StudentHomeworkCompletion

function bindingMode(value: string): 'LESSON' | 'DATE' {
  if (value !== 'LESSON' && value !== 'DATE') throw new Error('Invalid homework fixture binding mode')
  return value
}
