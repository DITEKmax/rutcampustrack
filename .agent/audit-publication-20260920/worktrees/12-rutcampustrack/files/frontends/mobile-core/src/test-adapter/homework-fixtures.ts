import completedHomework from '../../fixtures/homework-completion-completed.json'
import homeworkFeed from '../../fixtures/homework-feed.json'
import undoneHomework from '../../fixtures/homework-completion-undone.json'
import type { StudentHomework, StudentHomeworkCompletion } from '../api/types'

export const fixtureHomeworkFeed = homeworkFeed satisfies StudentHomework
export const fixtureCompletedHomework = completedHomework satisfies StudentHomeworkCompletion
export const fixtureUndoneHomework = undoneHomework satisfies StudentHomeworkCompletion
