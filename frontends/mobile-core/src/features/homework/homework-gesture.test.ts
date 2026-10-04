import { describe, expect, it } from 'vitest'
import {
  beginHomeworkPointer, createHomeworkGesture, finishHomeworkPointer,
  moveHomeworkPointer, resetHomeworkGesture, stageHomeworkKey,
} from './homework-gesture'

function gesture(completed = false) { return createHomeworkGesture(completed, 274) }
function pointer(completed: boolean, dx: number, dy = 0) {
  const state = gesture(completed)
  beginHomeworkPointer(state, 1, 50, 100)
  moveHomeworkPointer(state, 1, 50 + dx, 100 + dy)
  return finishHomeworkPointer(state, 1)
}
describe('homework intentional completion gesture', () => {
  it('tap, short travel and wrong direction never change either persisted state', () => {
    for (const completed of [false, true]) {
      expect(pointer(completed, 0)).toBeNull()
      expect(pointer(completed, completed ? -200 : 200)).toBeNull()
      expect(pointer(completed, completed ? 274 : -274)).toBeNull()
    }
  })
  it('sufficient horizontal travel creates one explicit complete/undo intent', () => {
    expect(pointer(false, 240)).toBe(true)
    expect(pointer(true, -240)).toBe(false)
    const state = gesture()
    beginHomeworkPointer(state, 8, 50, 100)
    moveHomeworkPointer(state, 8, 310, 100)
    expect(finishHomeworkPointer(state, 8)).toBe(true)
    expect(finishHomeworkPointer(state, 8)).toBeNull()
  })
  it('vertical/diagonal scroll cannot turn into completion after reaching the end', () => {
    const state = gesture()
    beginHomeworkPointer(state, 1, 50, 100)
    moveHomeworkPointer(state, 1, 51, 120)
    moveHomeworkPointer(state, 1, 320, 120)
    expect(finishHomeworkPointer(state, 1)).toBeNull()
    expect(pointer(false, 240, 230)).toBeNull()
    expect(pointer(true, -240, 230)).toBeNull()
  })
  it('cancel/lost capture, another pointer and zero travel cannot confirm', () => {
    const state = gesture()
    beginHomeworkPointer(state, 1, 50, 100)
    moveHomeworkPointer(state, 2, 320, 100)
    expect(finishHomeworkPointer(state, 2)).toBeNull()
    expect(finishHomeworkPointer(state, 1)).toBeNull()
    beginHomeworkPointer(state, 1, 50, 100)
    moveHomeworkPointer(state, 1, 320, 100)
    resetHomeworkGesture(state)
    expect(finishHomeworkPointer(state, 1)).toBeNull()
    const zero = createHomeworkGesture(false, 0)
    beginHomeworkPointer(zero, 1, 0, 0)
    moveHomeworkPointer(zero, 1, 1000, 0)
    expect(finishHomeworkPointer(zero, 1)).toBeNull()
  })
  it('standalone Enter/Space and partial keyboard travel never confirm', () => {
    for (const completed of [false, true]) {
      const state = gesture(completed)
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
      expect(stageHomeworkKey(state, ' ')).toBeNull()
      stageHomeworkKey(state, completed ? 'ArrowRight' : 'ArrowLeft')
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
      stageHomeworkKey(state, completed ? 'ArrowLeft' : 'ArrowRight')
      stageHomeworkKey(state, completed ? 'ArrowLeft' : 'ArrowRight')
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
    }
  })
  it('keyboard requires full directional staging then one Enter, reset discards it', () => {
    for (const completed of [false, true]) {
      const state = gesture(completed)
      for (let step = 0; step < 4; step += 1) expect(stageHomeworkKey(state, completed ? 'ArrowLeft' : 'ArrowRight')).toBeNull()
      expect(stageHomeworkKey(state, 'Enter')).toBe(!completed)
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
      for (let step = 0; step < 4; step += 1) stageHomeworkKey(state, completed ? 'ArrowLeft' : 'ArrowRight')
      stageHomeworkKey(state, 'Escape')
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
      for (let step = 0; step < 4; step += 1) stageHomeworkKey(state, completed ? 'ArrowLeft' : 'ArrowRight')
      resetHomeworkGesture(state)
      expect(stageHomeworkKey(state, 'Enter')).toBeNull()
    }
  })
  it('moving back below threshold disarms Enter and pointer-in-progress disallows keyboard', () => {
    const state = gesture()
    for (let step = 0; step < 4; step += 1) stageHomeworkKey(state, 'ArrowRight')
    stageHomeworkKey(state, 'ArrowLeft')
    expect(stageHomeworkKey(state, 'Enter')).toBeNull()
    beginHomeworkPointer(state, 1, 0, 0)
    moveHomeworkPointer(state, 1, 274, 0)
    expect(stageHomeworkKey(state, 'Enter')).toBeNull()
    expect(finishHomeworkPointer(state, 1)).toBe(true)
  })
})
