import { describe, expect, it } from 'vitest'
import { columnDndId, parseDndId, taskDndId } from './dndIds'

describe('dnd ids', () => {
  it('round-trips cards and columns', () => {
    expect(parseDndId(taskDndId(12))).toEqual({ kind: 'task', id: 12 })
    expect(parseDndId(columnDndId(3))).toEqual({ kind: 'column', id: 3 })
  })

  it('rejects anything else', () => {
    expect(parseDndId('task-')).toBeNull()
    expect(parseDndId('card-1')).toBeNull()
    expect(parseDndId(42)).toBeNull()
    expect(parseDndId('task-1-2')).toBeNull()
  })
})
