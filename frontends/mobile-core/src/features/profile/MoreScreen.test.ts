import { createSSRApp } from 'vue'
import { renderToString } from '@vue/server-renderer'
import { describe, expect, it } from 'vitest'
import MoreScreen from './MoreScreen.vue'

describe('MoreScreen route availability', () => {
  it('renders Statistics and Requests as actionable while Map remains unavailable', async () => {
    const html = await renderToString(createSSRApp(MoreScreen, { theme: 'dark' }))
    expect(html).toContain('Статистика')
    expect(html).toContain('Карта')
    expect(html).toContain('Заявки')
    expect((html.match(/<button[^>]*\sdisabled(?:=|\s|>)/gu) ?? [])).toHaveLength(1)
    expect(html).toContain('aria-disabled="true"')
  })
})
