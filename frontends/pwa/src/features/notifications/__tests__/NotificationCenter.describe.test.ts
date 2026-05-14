import { describe, expect, it } from 'vitest'

import { describeNotification, type NotificationRecord } from '../NotificationCenter'

function record(
  type: string,
  payload: Record<string, unknown>,
): NotificationRecord {
  return {
    id: 'n1',
    type,
    payload,
    receivedAt: new Date().toISOString(),
    read: false,
    archived: false,
  }
}

describe('describeNotification', () => {
  it('describes homework due reminders', () => {
    const result = describeNotification(
      record('homework.due_reminder', {
        user_id: 7,
        homework: {
          subject_name: 'Math',
          title: 'Essay',
          lesson_number: 2,
          lesson_date: '2026-05-05',
        },
      }),
    )

    expect(result.body).toContain('Math')
    expect(result.body).toContain('Essay')
    expect(result.body).toContain('05.05.2026')
  })

  it('describes homework weekly digests', () => {
    const result = describeNotification(
      record('homework.weekly_digest', {
        user_id: 7,
        total_count: 3,
        items: [
          { subject_name: 'Math', title: 'Essay', lesson_date: '2026-05-05' },
          { subject_name: 'History', title: 'Read', lesson_date: '2026-05-06' },
          { subject_name: 'Physics', title: 'Lab', lesson_date: '2026-05-06' },
        ],
      }),
    )

    expect(result.body).toContain('Math')
    expect(result.body).toContain('Essay')
    expect(result.body).toContain('History')
    expect(result.body).toContain('Read')
    expect(result.body).toContain('Physics')
    expect(result.body).toContain('Lab')
    expect(result.body).toContain('05.05.2026')
    expect(result.body).toContain('06.05.2026')
  })

  it('describes blocked lessons', () => {
    const result = describeNotification(
      record('lesson.blocked', {
        lesson_number: 3,
        start_time: '12:20',
        end_time: '13:50',
      }),
    )

    expect(result.title).toBe('Пара заблокирована')
    expect(result.body).toContain('№3')
    expect(result.body).toContain('12:20')
    expect(result.body).toContain('самостоятельная отметка невозможна')
  })
})
