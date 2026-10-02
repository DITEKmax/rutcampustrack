import { describe, expect, it, vi } from 'vitest'
import { HeadmanHomeworkApi, HeadmanHomeworkApiError } from './headman-homework-client'

const key = '97eb9070-a49d-45f9-9e72-b9d8fe06eab9'
const homework = { id: 91, title: 'На дату', description: 'Материалы', link: 'https://example.test/material',
  subjectId: 88, groupId: 7, semesterId: 12, publishedBy: 41, lessonDate: '2026-09-29',
  lessonNumber: null, bindingMode: 'DATE', revision: 4, archived: false }

describe('homework DATE and edit wire contract', () => {
  it('retains DATE/null placement, revision and archive while paging homework and history', async () => {
    const fetcher = vi.fn(async (path: RequestInfo | URL) => {
      if (String(path).includes('/history')) return Response.json({ content: [
        { id: 31, revision: 4, actorId: 42, occurredAt: '2026-09-27T12:00:00+03:00', action: 'EDITED', before: {}, after: homework },
      ], totalPages: 1 })
      return Response.json({ _embedded: { homeworkResponseList: [{ ...homework, archived: true }] }, page: { totalPages: 1 } })
    })
    const api = new HeadmanHomeworkApi({ accessToken: () => 'token', fetcher: fetcher as typeof fetch })
    expect(await api.listHomeworks(7, 12)).toEqual([{ ...homework, archived: true }])
    expect(await api.history(91)).toMatchObject([{ revision: 4, actorId: 42, action: 'EDITED' }])
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
      if (putCount === 2) return Response.json({ ...homework, title: 'Новое название', revision: 5 })
      return Response.json({ detail: 'revision conflict' }, { status: 409 })
    })
    const api = new HeadmanHomeworkApi({ accessToken: () => 'token', fetcher: fetcher as typeof fetch })
    await api.createHomework({ title: homework.title, description: homework.description, link: homework.link,
      subjectId: 88, groupId: 7, semesterId: 12, lessonDate: homework.lessonDate, lessonNumber: null, bindingMode: 'DATE', requestKey: key })
    expect(requests[0]?.body).toMatchObject({ bindingMode: 'DATE', lessonNumber: null, requestKey: key })
    const intent = Object.freeze({ title: 'Новое название', description: homework.description, link: homework.link,
      requestKey: key, expectedRevision: 4 })
    await expect(api.updateHomework(91, intent)).rejects.toThrow('response lost')
    await expect(api.updateHomework(91, intent)).resolves.toMatchObject({ id: 91, revision: 5 })
    expect(requests[1]?.body).toEqual(requests[2]?.body)
    expect(requests[2]?.body).toMatchObject({ expectedRevision: 4, requestKey: key, link: homework.link })
    await expect(api.updateHomework(91, intent)).rejects.toBeInstanceOf(HeadmanHomeworkApiError)
    expect(requests[3]?.body).toEqual(requests[2]?.body)
  })

  it('keeps a LESSON publication pending only for the same accepted intent and never invents success from another receipt', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(Response.json({ homeworkId: '91', bindingId: '501', requestKey: key, state: 'PENDING' }, { status: 202 }))
      .mockResolvedValueOnce(Response.json({ homeworkId: '91', bindingId: '501', requestKey: '3bbe4b81-42e1-4bcf-a963-41da6f379c02', state: 'PENDING' }, { status: 202 }))
    const api = new HeadmanHomeworkApi({ accessToken: () => 'token', fetcher: fetcher as typeof fetch })
    const input = { title: homework.title, subjectId: 88, groupId: 7, semesterId: 12, lessonDate: homework.lessonDate,
      lessonNumber: 2, bindingMode: 'LESSON' as const, requestKey: key }
    await expect(api.createHomework(input)).resolves.toBeNull()
    await expect(api.createHomework(input)).rejects.toThrow('некорректное подтверждение')
  })
})
