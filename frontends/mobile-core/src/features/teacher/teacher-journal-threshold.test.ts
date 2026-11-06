import { describe, expect, it } from 'vitest'
import { journalThreshold, journalThresholdResult } from './teacher-journal-threshold'

describe('journal threshold validation', () => {
  it('accepts bounded finite decimal values with comma, dot and surrounding whitespace', () => {
    for (const [input, expected] of [
      ['0', 0],
      ['100', 100],
      [' 25,5 ', 25.5],
      ['.5', 0.5],
      ['99.9', 99.9],
    ] as const)
      expect(journalThreshold(input)).toEqual({ state: 'valid', value: expected })
    for (const input of ['-1', '100.1', 'NaN', 'Infinity', '1e2', '1,2.3', '3 0', 'text'])
      expect(journalThreshold(input)).toEqual({ state: 'invalid', value: null })
    expect(journalThreshold(' \t ')).toEqual({ state: 'empty', value: null })
  })

  it('compares the unrounded server percentage and leaves unavailable values neutral', () => {
    const metric = { numerator: 1, denominator: 3, percent: 33.333333 }
    expect(journalThresholdResult(metric, journalThreshold('33.34'))).toBe('below')
    expect(journalThresholdResult(metric, journalThreshold('33.333333'))).toBe('meets')
    for (const unavailable of [
      null,
      undefined,
      { ...metric, denominator: 0 },
      { ...metric, percent: NaN },
      { ...metric, percent: Infinity },
    ])
      expect(journalThresholdResult(unavailable, journalThreshold('0'))).toBeNull()
    expect(journalThresholdResult(metric, journalThreshold(''))).toBeNull()
    expect(journalThresholdResult(metric, journalThreshold('101'))).toBeNull()
  })
})
