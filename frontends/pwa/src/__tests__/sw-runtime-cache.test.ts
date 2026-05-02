import { describe, it, expect } from 'vitest'
import { isHeadmanApiRequest } from '../sw-runtime-cache'

describe('isHeadmanApiRequest — headman API route matcher', () => {
  const url = (p: string) => new URL(`https://ruttrack.site${p}`)

  // Positive cases
  it('matches /api/academic/groups/:id/members', () => {
    expect(isHeadmanApiRequest(url('/api/academic/groups/42/members'))).toBe(true)
  })
  it('matches /api/academic/groups/:id/subjects', () => {
    expect(isHeadmanApiRequest(url('/api/academic/groups/42/subjects'))).toBe(true)
  })
  it('matches /api/academic/groups/:id/teachers', () => {
    expect(isHeadmanApiRequest(url('/api/academic/groups/42/teachers'))).toBe(true)
  })
  it('matches /api/academic/subjects with query', () => {
    expect(isHeadmanApiRequest(url('/api/academic/subjects?page=0&size=50'))).toBe(true)
  })
  it('matches /api/academic/subjects/:id', () => {
    expect(isHeadmanApiRequest(url('/api/academic/subjects/7'))).toBe(true)
  })
  it('matches /api/academic/thresholds/resolve', () => {
    expect(isHeadmanApiRequest(url('/api/academic/thresholds/resolve?groupId=1&subjectId=2'))).toBe(true)
  })
  it('matches /api/attendance/reports/journal', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/reports/journal?groupId=1&subjectId=2&dateFrom=2026-04-13&dateTo=2026-04-13'))).toBe(true)
  })
  it('matches /api/attendance/reports/headman-weekly/weeks metadata endpoint', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/reports/headman-weekly/weeks'))).toBe(true)
  })
  it('matches /api/attendance/excuses/pending', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/excuses/pending?groupId=42'))).toBe(true)
  })
  it('matches /api/attendance/late-checkins/pending', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/late-checkins/pending?groupId=42'))).toBe(true)
  })

  // Negative cases
  it('does NOT match /api/auth/login', () => {
    expect(isHeadmanApiRequest(url('/api/auth/login'))).toBe(false)
  })
  it('does NOT match attendance mutation path /api/attendance/lessons/:id/students/:id', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/lessons/123/students/456'))).toBe(false)
  })
  it('does NOT match binary headman weekly export endpoint', () => {
    expect(isHeadmanApiRequest(url('/api/attendance/reports/headman-weekly/current?weekStart=2026-04-27&format=docx'))).toBe(false)
  })
  it('does NOT match student endpoint /api/academic/users/:id', () => {
    expect(isHeadmanApiRequest(url('/api/academic/users/1'))).toBe(false)
  })
})
