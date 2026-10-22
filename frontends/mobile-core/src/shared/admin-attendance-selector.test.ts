import { describe, expect, it } from 'vitest'
import {
  validateTicketRequest,
  reportFormat,
  type ReportDownloadTicketRequest,
} from './report-download-client'

const invalid = (value: unknown) => validateTicketRequest(value as ReportDownloadTicketRequest)

describe('ADMIN group attendance durable export selector', () => {
  it('keeps the complete filtered sort across all five formats', () => {
    for (const format of ['html', 'pdf', 'docx', 'xlsx', 'png'] as const) {
      const request = {
        kind: 'ADMIN_GROUP_ATTENDANCE' as const,
        adminGroupAttendance: {
          semesterId: '9223372036854775807',
          search: 'ИВТ',
          sort: 'PRESENT_OR_EXCUSED_PERCENT' as const,
          direction: 'DESC' as const,
          format,
        },
      }
      expect(() => validateTicketRequest(request)).not.toThrow()
      expect(reportFormat(request)).toBe(format)
    }
  })

  it('rejects unknown, mixed, paginated and lossy identities before sending a job', () => {
    const valid = {
      semesterId: '7',
      search: '',
      sort: 'GROUP_NAME',
      direction: 'ASC',
      format: 'html',
    }
    for (const change of [
      { semesterId: 7 },
      { semesterId: '9223372036854775808' },
      { semesterId: '0' },
      { sort: 'FUTURE' },
      { direction: 'SIDEWAYS' },
      { format: 'csv' },
      { page: 1 },
      { groupId: '10' },
      { search: 'x'.repeat(201) },
    ]) {
      expect(() =>
        invalid({
          kind: 'ADMIN_GROUP_ATTENDANCE',
          adminGroupAttendance: { ...valid, ...change },
        }),
      ).toThrow()
    }
    expect(() =>
      invalid({
        kind: 'ADMIN_GROUP_ATTENDANCE',
        adminGroupAttendance: valid,
        adminGroups: { format: 'html' },
      }),
    ).toThrow()
  })
})
