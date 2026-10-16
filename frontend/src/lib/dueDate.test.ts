import { describe, expect, it } from 'vitest'
import { addDays, dueStatus, formatDueDate, toIsoDate } from './dueDate'

describe('dueStatus', () => {
  const today = '2026-10-14'

  it('has no status without a due date', () => {
    expect(dueStatus(null, today)).toBe('none')
  })

  it('classifies relative to today', () => {
    expect(dueStatus('2026-10-13', today)).toBe('overdue')
    expect(dueStatus('2026-10-14', today)).toBe('today')
    expect(dueStatus('2026-10-15', today)).toBe('soon')
    expect(dueStatus('2026-10-17', today)).toBe('soon') // today + 3 days is still "soon"
    expect(dueStatus('2026-10-18', today)).toBe('later')
  })
})

describe('addDays', () => {
  it('crosses month and year boundaries', () => {
    expect(addDays('2026-10-30', 3)).toBe('2026-11-02')
    expect(addDays('2026-12-30', 3)).toBe('2027-01-02')
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29') // leap year
  })

  it('goes backwards too', () => {
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })
})

describe('toIsoDate', () => {
  it('uses the local calendar date with zero padding', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05')
    expect(toIsoDate(new Date(2026, 11, 31))).toBe('2026-12-31')
  })
})

describe('formatDueDate', () => {
  it('formats without any time-zone conversion', () => {
    expect(formatDueDate('2026-10-14')).toBe('Oct 14, 2026')
    expect(formatDueDate('2026-01-01')).toBe('Jan 1, 2026')
  })
})
