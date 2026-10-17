import { afterEach, describe, expect, it, vi } from 'vitest'
import { AdminGroupsClient, adminGroupProgramTypes } from './admin-groups-client'
import { createAdminGroupsFixture } from '../../dev/admin-groups-fixtures'

afterEach(() => vi.unstubAllGlobals())
function fixture() {
  vi.stubGlobal('document', { documentElement: { dataset: {} } })
  vi.stubGlobal('window', { location: { origin: 'https://synthetic.invalid' } })
  vi.stubGlobal('fetch', () => {
    throw new Error('Unexpected real network')
  })
  return createAdminGroupsFixture('active')
}
describe('registry server filtering and ordering contract', () => {
  it('uses the same nine programme mappings across filters, metadata and paginated results', async () => {
    const client = fixture()
    for (const program of adminGroupProgramTypes) {
      const result = await client.listGroups({
        status: 'ACTIVE',
        studyMode: program.studyMode,
        educationLevel: program.educationLevel,
        size: 1,
      })
      expect(result.items[0]?.programTypeDigit).toBe(program.digit)
      expect(result.totalElements).toBeGreaterThan(1)
      const next = await client.listGroups({
        status: 'ACTIVE',
        studyMode: program.studyMode,
        educationLevel: program.educationLevel,
        size: 1,
        page: 1,
      })
      expect(next.items[0]?.programTypeDigit).toBe(program.digit)
      expect(next.items[0]?.id).not.toBe(result.items[0]?.id)
      expect(next.totalElements).toBe(result.totalElements)
      expect(next.activeCount).toBe(result.totalElements)
    }
  })
  it('orders the complete registry before slicing pages and combines course with real programme predicates', async () => {
    const client = fixture()
    const first = await client.listGroups({
      status: 'ACTIVE',
      sort: ['programType,desc', 'currentCourse,asc'],
      size: 3,
    })
    const next = await client.listGroups({
      status: 'ACTIVE',
      sort: ['programType,desc', 'currentCourse,asc'],
      size: 3,
      page: 1,
    })
    expect(first.items.map((item) => item.programTypeDigit)).toEqual([9, 9, 8])
    expect(next.items.map((item) => item.programTypeDigit)).toEqual([8, 7, 7])
    expect(new Set([...first.items, ...next.items].map((item) => item.id)).size).toBe(6)
    const filtered = await client.listGroups({
      course: 3,
      studyMode: 'EXTRAMURAL',
      educationLevel: 'BACHELOR',
      size: 20,
    })
    expect(
      filtered.items.every((item) => item.currentCourse === 3 && item.programTypeDigit === 3),
    ).toBe(true)
    expect(filtered.activeCount).toBe(filtered.items.length)
  })
  it('keeps unknown additive metadata unknown when reading an older accepted response', async () => {
    const original = await fixture().listGroups({ size: 1 })
    const legacy = {
      ...original,
      items: original.items.map((item) => {
        const legacyItem: Record<string, unknown> = { ...item }
        delete legacyItem.programTypeDigit
        delete legacyItem.studyMode
        delete legacyItem.educationLevel
        return legacyItem
      }),
    }
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async () => Response.json(legacy),
    })
    const result = await client.listGroups()
    expect(result.items[0]?.programTypeDigit).toBeNull()
    expect(result.items[0]?.studyMode).toBeNull()
    expect(result.items[0]?.educationLevel).toBeNull()
  })
})

describe('course limits preserve programme types and legacy reads', () => {
  it('accepts course5, rejects course6 before requests, and keeps programme9 independent', async () => {
    const client = fixture()
    await expect(client.listGroups({ course: 5 })).resolves.toBeDefined()
    await expect(client.listGroups({ course: 6 })).rejects.toThrow('от 1 до 5')
    await expect(
      client.createGroup({ alphabeticCode: 'УИТ', numericCode: '591', trainingDurationYears: 5 }),
    ).resolves.toMatchObject({ currentCourse: 5, programTypeDigit: 9 })
    expect(() =>
      client.createGroup({ alphabeticCode: 'УИТ', numericCode: '691', trainingDurationYears: 9 }),
    ).toThrow('от 1 до 5')
  })
  it('retains a historical course6 response without rewriting or archiving it', async () => {
    const page = await fixture().listGroups({ size: 1 })
    const legacy = {
      ...page,
      items: page.items.map((item) => ({ ...item, currentCourse: 6, numericCode: '611' })),
    }
    const client = new AdminGroupsClient({
      accessToken: () => null,
      fetcher: async () => Response.json(legacy),
    })
    await expect(client.listGroups()).resolves.toMatchObject({
      items: [{ currentCourse: 6, numericCode: '611', status: 'ACTIVE' }],
    })
  })
})

describe('explicit active group metadata correction', () => {
  it('preserves a known duration by omission and allows explicit unknown-duration correction', async () => {
    const current = (await fixture().listGroups({ size: 1 })).items[0]!
    const fetcher = vi.fn<typeof fetch>(async () => new Response(null, { status: 204 }))
    const client = new AdminGroupsClient({ accessToken: () => null, fetcher })
    await client.renameGroup(current, 'УИТ-191')
    expect(JSON.parse(String(fetcher.mock.calls[0]?.[1]?.body))).toEqual({
      name: 'УИТ-191',
      active: true,
    })
    const legacy = {
      ...current,
      durationStatus: 'LEGACY_UNKNOWN' as const,
      trainingDurationYears: null,
    }
    await expect(client.renameGroup(legacy, 'УИТ-591')).rejects.toThrow()
    await client.renameGroup(legacy, 'УИТ-591', 5)
    expect(JSON.parse(String(fetcher.mock.calls[1]?.[1]?.body))).toEqual({
      name: 'УИТ-591',
      active: true,
      trainingDurationYears: 5,
    })
    await expect(client.renameGroup(legacy, 'УИТ-691', 5)).rejects.toBeInstanceOf(RangeError)
    await expect(client.renameGroup(legacy, 'УИТ-591', 6)).rejects.toThrow('от 1 до 5')
    await expect(client.renameGroup({ ...current, status: 'ARCHIVED' }, 'УИТ-191')).rejects.toThrow(
      'активной',
    )
    expect(fetcher).toHaveBeenCalledTimes(2)
  })
})
