import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it } from 'vitest'
import ExcuseRequestScreen from './ExcuseRequestScreen.vue'
import RequestsScreen from './RequestsScreen.vue'
import type { RequestDetail, RequestLessonOption, RequestReasonOption } from './types'

const pendingRequest: RequestDetail = {
  summary: {
    id: 'request-1',
    kind: 'EXCUSE',
    status: 'PENDING',
    origin: 'MANUAL',
    createdAt: '2026-09-01T06:40:00Z',
    canCancel: true,
    lessons: [
      {
        id: 'lesson-1',
        subjectName: 'Основы программирования',
        subjectType: 'LECTURE',
        date: '2026-09-01',
        startsAt: '10:40:00',
        endsAt: '12:10:00',
      },
    ],
  },
  reason: 'Деталь причины не является решением',
  decision: null,
}

async function renderScreen(overrides: Partial<{
  bucket: 'open' | 'archive'
  requests: readonly RequestDetail[]
  loading: boolean
  error: string | null
  offline: boolean
  access: 'allowed' | 'forbidden' | 'no-active-semester'
}> = {}): Promise<string> {
  const props = {
    bucket: 'open' as const,
    requests: [pendingRequest] as readonly RequestDetail[],
    loading: false,
    error: null,
    offline: false,
    access: 'allowed' as const,
    ...overrides,
  }
  return renderToString(createSSRApp(RequestsScreen, props))
}


const excuseReasons: RequestReasonOption[] = [{ code: 'ILLNESS', label: 'Болезнь', commentRequired: false }]
const selectedExcuseLesson: RequestLessonOption = {
  lesson: {
    id: 'lesson-1',
    subjectName: 'Основы программирования',
    subjectType: 'LECTURE',
    date: '2026-09-01',
    startsAt: '10:40:00',
    endsAt: '12:10:00',
  },
  excuseEligible: true,
}

async function renderExcuse(overrides: Partial<{
  lessons: readonly RequestLessonOption[]
  lessonIds: readonly string[]
  reasons: readonly RequestReasonOption[]
  reason: string | null
  comment: string
}> = {}): Promise<string> {
  return renderToString(createSSRApp(ExcuseRequestScreen, {
    access: 'allowed',
    lessons: [selectedExcuseLesson],
    lessonIds: ['lesson-1'],
    reasons: excuseReasons,
    reason: 'ILLNESS',
    comment: '',
    files: [],
    fileLimits: null,
    ...overrides,
  }))
}

describe('RequestsScreen rendered states', () => {
  it('renders the open card with a worded status, lesson metadata and cancellation intent', async () => {
    const html = await renderScreen()
    expect(html).toContain('Заявки')
    expect(html).toContain('Открытые')
    expect(html).toContain('На рассмотрении')
    expect(html).toContain('Основы программирования')
    expect(html).toContain('Отменить заявку')
    expect(html).not.toContain('request-card__decision')
    expect(html).not.toContain('Деталь причины не является решением')
    expect(html).not.toContain('type="checkbox"')
  })

  it('keeps archive empty copy distinct and makes offline submission unavailable', async () => {
    const archive = await renderScreen({ bucket: 'archive', requests: [] })
    expect(archive).toContain('Архив пуст')
    expect(archive).not.toContain('Нет заявок на рассмотрении')

    const offline = await renderScreen({ offline: true })
    expect(offline).toContain('Офлайн · показываем сохранённые данные')
    expect(offline).toContain('Подача заявок снова станет доступна онлайн.')
    expect(offline).toContain('disabled')
  })


  it('blocks a retained excuse selection when refreshed server eligibility no longer includes it', async () => {
    const first = await renderExcuse()
    expect(first).toContain('Отправить заявку')
    expect(first).not.toMatch(/class="requests-submit-action"[^>]*disabled/)

    const refreshed = await renderExcuse({ lessons: [{
      ...selectedExcuseLesson,
      excuseEligible: false,
      unavailableReason: 'Пара уже закрыта для заявки.',
    }] })
    expect(refreshed).toContain('Пара уже закрыта для заявки.')
    expect(refreshed).toContain('Убрать из заявки')
    expect(refreshed).toMatch(/class="requests-submit-action"[^>]*disabled/)

    const refreshedReasons = await renderExcuse({ reasons: [] })
    expect(refreshedReasons).toContain('Причины пока недоступны.')
    expect(refreshedReasons).toMatch(/class="requests-submit-action"[^>]*disabled/)

    const reasonNoLongerAllowed = await renderExcuse({ reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: false }] })
    expect(reasonNoLongerAllowed).toContain('Выбранная причина больше недоступна.')
    expect(reasonNoLongerAllowed).toMatch(/class="requests-submit-action"[^>]*disabled/)
  })

  it('renders a removable recovery control for a missing retained lesson and keeps submission gated', async () => {
    const missing = await renderExcuse({ lessonIds: ['lesson-missing'] })

    expect(missing).toContain('Есть недоступные выбранные пары')
    expect(missing).toContain('Сохранённая пара больше недоступна')
    expect(missing).toContain('Убрать из заявки')
    expect(missing).not.toContain('lesson-missing')
    expect(missing).toMatch(/class="requests-submit-action"[^>]*disabled/)
  })

  it('gates blank comments from server metadata instead of reason-code inference', async () => {
    const requiredReasons: RequestReasonOption[] = [{ code: 'ILLNESS', label: 'Болезнь', commentRequired: true }]
    const requiredBlank = await renderExcuse({ reasons: requiredReasons })
    expect(requiredBlank).toContain('Добавь комментарий, чтобы отправить заявку.')
    expect(requiredBlank).toMatch(/class="requests-submit-action"[^>]*disabled/)

    const requiredFilled = await renderExcuse({ reasons: requiredReasons, comment: 'Справка будет приложена.' })
    expect(requiredFilled).not.toMatch(/class="requests-submit-action"[^>]*disabled/)

    const optionalBlank = await renderExcuse({ reasons: [{ code: 'OTHER', label: 'Другое', commentRequired: false }], reason: 'OTHER' })
    expect(optionalBlank).not.toMatch(/class="requests-submit-action"[^>]*disabled/)
  })

  it('announces loading, error and access boundaries as visible states', async () => {
    expect(await renderScreen({ loading: true })).toContain('Загружаем заявки')
    expect(await renderScreen({ error: 'Сервис временно недоступен' })).toContain('Сервис временно недоступен')
    expect(await renderScreen({ access: 'forbidden' })).toContain('Раздел недоступен')
    expect(await renderScreen({ access: 'no-active-semester' })).toContain('Нет активного семестра')
  })
})

