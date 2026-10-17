import { describe, expect, it, vi } from 'vitest'
import { HeadmanHomeworkApi, HeadmanHomeworkApiError } from './headman-homework-client'
import {
  reuseOrCreateAssistantHomeworkIntent,
  withAssistantHomeworkPublicationReceipt,
} from './assistant-homework-create-intent'

const key = '97eb9070-a49d-45f9-9e72-b9d8fe06eab9'
const homework = {
  id: 91,
  bindingId: 501,
  requestKey: key,
  title: 'На дату',
  description: 'Материалы',
  link: 'https://example.test/material',
  subjectId: 88,
  groupId: 7,
  semesterId: 12,
  publishedBy: 41,
  lessonDate: '2026-09-29',
  lessonNumber: null,
  bindingMode: 'DATE',
  revision: 4,
  archived: false,
}

describe('Headman Homework r2 additive boundary', () => {
  it('preserves DATE type and durable binding receipt on create/reload/content-only edit', async () => {
    const current = { ...homework, lessonType: 'LAB' }
    const calls: RequestInit[] = []
    const fetcher = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
      calls.push(init!)
      return Response.json(current, { status: init?.method === 'POST' ? 201 : 200 })
    })
    const api = new HeadmanHomeworkApi({ accessToken: () => null, fetcher })
    await expect(
      api.createHomework({
        title: 'На дату',
        subjectId: 88,
        groupId: 7,
        semesterId: 12,
        lessonDate: '2026-09-29',
        lessonNumber: null,
        bindingMode: 'DATE',
        lessonType: 'LAB',
        requestKey: key,
      }),
    ).resolves.toMatchObject({ state: 'ACTIVE', homework: { lessonType: 'LAB', bindingId: 501 } })
    expect(JSON.parse(String(calls[0]?.body))).toMatchObject({
      lessonType: 'LAB',
      lessonNumber: null,
    })
    await expect(api.getHomework(91)).resolves.toMatchObject({ lessonType: 'LAB' })
    await api.updateHomework(91, { title: 'На дату', requestKey: key, expectedRevision: 4 })
    expect(JSON.parse(String(calls[2]?.body))).not.toHaveProperty('lessonType')
    const broken = new HeadmanHomeworkApi({
      accessToken: () => null,
      fetcher: async () => Response.json({ ...current, bindingId: null }, { status: 201 }),
    })
    await expect(
      broken.createHomework({
        title: 'На дату',
        subjectId: 88,
        groupId: 7,
        semesterId: 12,
        lessonDate: '2026-09-29',
        lessonNumber: null,
        bindingMode: 'DATE',
        lessonType: 'LAB',
        requestKey: key,
      }),
    ).rejects.toThrow()
  })
  it('adds explicit archive scope only to requested collection/detail reads', async () => {
    const paths: string[] = []
    const fetcher: typeof fetch = async (input) => {
      const path = String(input)
      paths.push(path)
      return Response.json(
        path.includes('/91')
          ? { ...homework, archived: true }
          : { _embedded: { homeworkResponseList: [{ ...homework, archived: true }] } },
      )
    }
    const api = new HeadmanHomeworkApi({ accessToken: () => null, fetcher })
    await api.listHomeworks(7, 12, true)
    await api.getHomework(91, true)
    await api.listHomeworks(7, 12)
    expect(paths[0]).toContain('archived=true')
    expect(paths[1]).toBe('/api/academic/homeworks/91?archived=true')
    expect(paths[2]).not.toContain('archived')
  })
  it('retains exact next occurrence tokens and frozen selected guards across a lost reply', async () => {
    const posts: string[] = []
    let count = 0
    const fetcher: typeof fetch = async (input, init) => {
      if (String(input).includes('/lessons/next'))
        return Response.json({
          lessonId: 30,
          occurrenceId: '71',
          occurrenceRevision: '9007199254740993',
          groupId: 7,
          subjectId: 88,
          semesterId: 12,
          lessonType: 'LECTURE',
          date: '2026-09-29',
          lessonNumber: 2,
          startTime: '10:00',
          endTime: '11:30',
        })
      posts.push(String(init?.body))
      count += 1
      if (count === 1) throw new TypeError('lost reply')
      return Response.json({ ...homework, bindingMode: 'LESSON', lessonNumber: 2 })
    }
    const api = new HeadmanHomeworkApi({ accessToken: () => null, fetcher })
    const next = await api.nextHomeworkLesson(7, 12, 88, 'LECTURE', '2026-09-02')
    expect(next?.occurrenceRevision).toBe('9007199254740993')
    const input = {
      title: 'Задание',
      subjectId: 88,
      groupId: 7,
      semesterId: 12,
      lessonDate: '2026-09-29',
      lessonNumber: 2,
      bindingMode: 'LESSON' as const,
      lessonType: 'LECTURE' as const,
      selectedOccurrenceId: next!.occurrenceId!,
      selectedOccurrenceRevision: next!.occurrenceRevision!,
      requestKey: key,
    }
    await expect(api.createHomework(input)).rejects.toThrow('lost reply')
    await api.createHomework(input)
    expect(posts[0]).toBe(posts[1])
    expect(JSON.parse(posts[0]!)).toMatchObject({
      selectedOccurrenceId: '71',
      selectedOccurrenceRevision: '9007199254740993',
    })
  })
  it('forbids DATE occurrence guards and rejects invalid or incomplete placement before transport', async () => {
    const fetcher = vi.fn()
    const api = new HeadmanHomeworkApi({ accessToken: () => null, fetcher })
    const input = {
      title: 'Задание',
      subjectId: 88,
      groupId: 7,
      semesterId: 12,
      lessonDate: '2026-09-29',
      lessonNumber: null,
      bindingMode: 'DATE' as const,
      requestKey: key,
      selectedOccurrenceId: '71',
      selectedOccurrenceRevision: '3',
    }
    await expect(api.createHomework(input)).rejects.toThrow()
    await expect(
      api.updateHomework(91, {
        title: 'Задание',
        requestKey: key,
        expectedRevision: 4,
        bindingMode: 'LESSON',
        lessonDate: '2026-02-30',
        lessonNumber: 2,
      }),
    ).rejects.toThrow()
    expect(fetcher).not.toHaveBeenCalled()
  })
})

describe('homework DATE and edit wire contract', () => {
  it('retains DATE/null placement, revision and archive while paging homework and history', async () => {
    const fetcher = vi.fn(async (path: RequestInfo | URL) => {
      if (String(path).includes('/history'))
        return Response.json({
          content: [
            {
              id: 31,
              revision: 4,
              actorId: 42,
              actorDisplayName: 'Кузнецов Михаил Андреевич',
              occurredAt: '2026-09-27T12:00:00+03:00',
              action: 'EDITED',
              before: {},
              after: homework,
            },
          ],
          totalPages: 1,
        })
      return Response.json({
        _embedded: { homeworkResponseList: [{ ...homework, archived: true }] },
        page: { totalPages: 1 },
      })
    })
    const api = new HeadmanHomeworkApi({
      accessToken: () => 'token',
      fetcher: fetcher as typeof fetch,
    })
    expect(await api.listHomeworks(7, 12)).toEqual([{ ...homework, archived: true }])
    expect(await api.history(91)).toMatchObject([
      { revision: 4, actorId: 42, actorDisplayName: 'Кузнецов Михаил Андреевич', action: 'EDITED' },
    ])
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('sends exact keyed DATE creation and edit revision, preserves the request after loss and surfaces a stale conflict', async () => {
    const requests: { path: string; body: unknown }[] = []
    let putCount = 0
    const fetcher = vi.fn(async (path: RequestInfo | URL, init?: RequestInit) => {
      requests.push({ path: String(path), body: JSON.parse(String(init?.body)) })
      if (init?.method === 'POST') return Response.json(homework, { status: 201 })
      putCount += 1
      if (putCount === 1) throw new TypeError('response lost after server accepted edit')
      if (putCount === 2)
        return Response.json({ ...homework, title: 'Новое название', revision: 5 })
      return Response.json({ detail: 'revision conflict' }, { status: 409 })
    })
    const api = new HeadmanHomeworkApi({
      accessToken: () => 'token',
      fetcher: fetcher as typeof fetch,
    })
    await expect(
      api.createHomework({
        title: homework.title,
        description: homework.description,
        link: homework.link,
        subjectId: 88,
        groupId: 7,
        semesterId: 12,
        lessonDate: homework.lessonDate,
        lessonNumber: null,
        bindingMode: 'DATE',
        requestKey: key,
      }),
    ).resolves.toMatchObject({ state: 'ACTIVE', homework: { id: 91 } })
    expect(requests[0]?.body).toMatchObject({
      bindingMode: 'DATE',
      lessonNumber: null,
      requestKey: key,
    })
    const intent = Object.freeze({
      title: 'Новое название',
      description: homework.description,
      link: homework.link,
      requestKey: key,
      expectedRevision: 4,
    })
    await expect(api.updateHomework(91, intent)).rejects.toThrow('response lost')
    await expect(api.updateHomework(91, intent)).resolves.toMatchObject({ id: 91, revision: 5 })
    expect(requests[1]?.body).toEqual(requests[2]?.body)
    expect(requests[2]?.body).toMatchObject({
      expectedRevision: 4,
      requestKey: key,
      link: homework.link,
    })
    await expect(api.updateHomework(91, intent)).rejects.toBeInstanceOf(HeadmanHomeworkApiError)
    expect(requests[3]?.body).toEqual(requests[2]?.body)
  })

  it('finishes a lost creation response after transfer using the same request and current placement', async () => {
    const moved = { ...homework, lessonDate: '2026-10-02', lessonNumber: 5, bindingMode: 'LESSON' }
    const fetcher = vi
      .fn()
      .mockRejectedValueOnce(new TypeError('response lost after creation'))
      .mockResolvedValueOnce(Response.json(moved))
    const api = new HeadmanHomeworkApi({
      accessToken: () => 'token',
      fetcher: fetcher as typeof fetch,
    })
    const input = {
      title: homework.title,
      subjectId: 88,
      groupId: 7,
      semesterId: 12,
      lessonDate: homework.lessonDate,
      lessonNumber: 2,
      bindingMode: 'LESSON' as const,
      requestKey: key,
    }
    await expect(api.createHomework(input)).rejects.toThrow('response lost')
    await expect(api.createHomework(input)).resolves.toMatchObject({
      state: 'ACTIVE',
      homework: moved,
    })
    expect(fetcher.mock.calls[0]?.[1]?.body).toEqual(fetcher.mock.calls[1]?.[1]?.body)
  })

  it('retains PENDING identity in the intent and completes its same-key replay after transfer', async () => {
    const receipt = { homeworkId: '91', bindingId: '501', requestKey: key }
    const moved = { ...homework, lessonDate: '2026-10-02', lessonNumber: 5, bindingMode: 'LESSON' }
    const fetcher = vi
      .fn()
      .mockResolvedValueOnce(Response.json({ ...receipt, state: 'PENDING' }, { status: 202 }))
      .mockResolvedValueOnce(Response.json(moved))
    const api = new HeadmanHomeworkApi({
      accessToken: () => 'token',
      fetcher: fetcher as typeof fetch,
    })
    const context = {
      userId: 41,
      groupId: 7,
      semesterId: 12,
      subjectId: 88,
      selectedDate: homework.lessonDate,
      lessonId: 300,
      lessonDate: homework.lessonDate,
      lessonNumber: 2,
      bindingMode: 'LESSON' as const,
    }
    const values = { title: homework.title, description: '', link: '' }
    const original = reuseOrCreateAssistantHomeworkIntent(null, context, values, () => key)!
    const pending = await api.createHomework(original.input, original.receipt)
    expect(pending.state).toBe('PENDING')
    if (pending.state !== 'PENDING') throw new Error('expected pending publication')
    const accepted = withAssistantHomeworkPublicationReceipt(original, pending.receipt)
    const retry = reuseOrCreateAssistantHomeworkIntent(
      accepted,
      context,
      { ...values, title: 'Changed' },
      () => 'another-key',
    )!
    expect(retry).toBe(accepted)
    expect(retry.input).toBe(original.input)
    expect(retry.receipt).toEqual(receipt)
    expect(Object.isFrozen(retry.receipt)).toBe(true)
    await expect(api.createHomework(retry.input, retry.receipt)).resolves.toMatchObject({
      state: 'ACTIVE',
      homework: moved,
    })
    expect(fetcher.mock.calls[0]?.[1]?.body).toEqual(fetcher.mock.calls[1]?.[1]?.body)
  })

  it('rejects a different key, accepted identity or immutable scope in pending and active responses', async () => {
    const receipt = { homeworkId: '91', bindingId: '501', requestKey: key }
    const input = {
      title: homework.title,
      subjectId: 88,
      groupId: 7,
      semesterId: 12,
      lessonDate: homework.lessonDate,
      lessonNumber: 2,
      bindingMode: 'LESSON' as const,
      requestKey: key,
    }
    const otherKey = '3bbe4b81-42e1-4bcf-a963-41da6f379c02'
    for (const wrong of [
      { ...receipt, requestKey: otherKey },
      { ...receipt, homeworkId: '92' },
      { ...receipt, bindingId: '502' },
    ]) {
      const fetcher = vi
        .fn()
        .mockResolvedValue(Response.json({ ...wrong, state: 'PENDING' }, { status: 202 }))
      const api = new HeadmanHomeworkApi({
        accessToken: () => 'token',
        fetcher: fetcher as typeof fetch,
      })
      await expect(api.createHomework(input, receipt)).rejects.toThrow('некорректное подтверждение')
    }
    for (const wrong of [
      { ...homework, requestKey: otherKey },
      { ...homework, id: 92 },
      { ...homework, bindingId: 502 },
      { ...homework, groupId: 8 },
      { ...homework, subjectId: 89 },
      { ...homework, semesterId: 13 },
    ]) {
      const fetcher = vi.fn().mockResolvedValue(Response.json(wrong))
      const api = new HeadmanHomeworkApi({
        accessToken: () => 'token',
        fetcher: fetcher as typeof fetch,
      })
      await expect(api.createHomework(input, receipt)).rejects.toThrow()
    }
  })
})
