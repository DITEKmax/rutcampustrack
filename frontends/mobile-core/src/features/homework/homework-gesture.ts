/** A local gesture stages an intent; only the owning controller persists it. */
export interface HomeworkGesture {
  completed: boolean
  travel: number
  progress: number
  pointer: { id: number; x: number; y: number } | null
  canceled: boolean
}
export const HOMEWORK_COMMIT_PROGRESS = 0.8
export function createHomeworkGesture(completed: boolean, travel: number): HomeworkGesture {
  return { completed, travel: Math.max(0, travel), progress: 0, pointer: null, canceled: false }
}
export function beginHomeworkPointer(state: HomeworkGesture, id: number, x: number, y: number): void {
  state.progress = 0
  state.canceled = false
  state.pointer = { id, x, y }
}
export function moveHomeworkPointer(state: HomeworkGesture, id: number, x: number, y: number): void {
  const origin = state.pointer
  if (!origin || origin.id !== id || state.canceled || state.travel <= 0) return
  const dx = x - origin.x
  const dy = Math.abs(y - origin.y)
  // A scroll or diagonal movement cannot become a completion at its end.
  if (dy >= 8 && dy > Math.abs(dx) * 0.75) {
    state.canceled = true
    state.progress = 0
    return
  }
  state.progress = Math.min(1, Math.max(0, dx * (state.completed ? -1 : 1) / state.travel))
}
export function finishHomeworkPointer(state: HomeworkGesture, id: number): boolean | null {
  if (state.pointer?.id !== id) return null
  const desired = !state.canceled && state.travel > 0 && state.progress >= HOMEWORK_COMMIT_PROGRESS ? !state.completed : null
  resetHomeworkGesture(state)
  return desired
}
export function resetHomeworkGesture(state: HomeworkGesture): void {
  state.pointer = null
  state.progress = 0
  state.canceled = false
}
export function stageHomeworkKey(state: HomeworkGesture, key: string): boolean | null {
  if (state.pointer) return null
  if (key === 'Escape') { resetHomeworkGesture(state); return null }
  if (key === 'ArrowRight' || key === 'ArrowLeft') {
    const forward = key === (state.completed ? 'ArrowLeft' : 'ArrowRight')
    state.progress = Math.min(1, Math.max(0, state.progress + (forward ? 0.25 : -0.25)))
  } else if (key === 'Enter') {
    const desired = state.progress >= HOMEWORK_COMMIT_PROGRESS ? !state.completed : null
    resetHomeworkGesture(state)
    return desired
  }
  return null
}
