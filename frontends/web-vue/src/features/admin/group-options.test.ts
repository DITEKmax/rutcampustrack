import { describe, expect, it, vi } from 'vitest'
import { AdminUsersClient } from '../../../../mobile-core/src/features/admin-users/admin-users-client'

function setup(item: unknown) {
  const fetcher = vi.fn<typeof fetch>().mockResolvedValue(
    new Response(
      JSON.stringify({
        items: [item],
        number: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      }),
    ),
  )
  return { fetcher, client: new AdminUsersClient({ fetcher, accessToken: () => null }) }
}
describe('administrator directory group lookup', () => {
  it('keeps archived groups out of default new-role choices, including legacy responses', async () => {
    const old = setup({ id: 7, name: 'ПРТ-119' })
    expect((await old.client.listGroups()).items[0]?.archived).toBe(false)
    expect(String(old.fetcher.mock.calls[0]?.[0])).not.toContain('includeArchived')
    await expect(
      setup({ id: 7, name: 'ПРТ-119', archived: true }).client.listGroups(),
    ).rejects.toThrow('Архивная группа')
  })
  it('explicitly includes and labels archived groups for directory filtering', async () => {
    const { client, fetcher } = setup({ id: 7, name: 'ПРТ-119', archived: true })
    expect((await client.listGroups('', 0, undefined, true)).items[0]?.archived).toBe(true)
    expect(String(fetcher.mock.calls[0]?.[0])).toContain('includeArchived=true')
  })
  it('never treats unconfirmed archival state as an active lookup result', async () => {
    await expect(
      setup({ id: 7, name: 'ПРТ-119' }).client.listGroups('', 0, undefined, true),
    ).rejects.toThrow('состояние группы')
    await expect(
      setup({ id: 7, name: 'ПРТ-119', archived: null }).client.listGroups(),
    ).rejects.toThrow('состояние группы')
  })
})
