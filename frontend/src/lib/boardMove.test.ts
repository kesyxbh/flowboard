import { describe, expect, it } from 'vitest'
import type { BoardDetail, Task } from '../api/types'
import { locateTask, moveTaskInBoard } from './boardMove'

// Same fixture as TaskMoveServiceTest in the backend: "todo" = [a, b, c], "doing" = [x].
function task(id: number, columnId: number, title: string, orderIndex: number): Task {
  return { id, columnId, title, description: null, assigneeId: null, priority: 'MEDIUM', dueDate: null, orderIndex }
}

const board: BoardDetail = {
  id: 1,
  name: 'Board',
  columns: [
    { id: 10, boardId: 1, name: 'todo', orderIndex: 0, tasks: [task(1, 10, 'a', 0), task(2, 10, 'b', 1), task(3, 10, 'c', 2)] },
    { id: 20, boardId: 1, name: 'doing', orderIndex: 1, tasks: [task(4, 20, 'x', 0)] },
  ],
}

function titles(b: BoardDetail, columnId: number): string[] {
  return b.columns.find((column) => column.id === columnId)!.tasks.map((t) => t.title)
}

function indexes(b: BoardDetail, columnId: number): number[] {
  return b.columns.find((column) => column.id === columnId)!.tasks.map((t) => t.orderIndex)
}

describe('moveTaskInBoard', () => {
  it('reorders inside one column', () => {
    const moved = moveTaskInBoard(board, 1, 10, 2) // a -> last
    expect(titles(moved, 10)).toEqual(['b', 'c', 'a'])
    expect(indexes(moved, 10)).toEqual([0, 1, 2])
  })

  it('moves across columns and closes the gap in the source column', () => {
    const moved = moveTaskInBoard(board, 2, 20, 0) // b -> top of doing
    expect(titles(moved, 20)).toEqual(['b', 'x'])
    expect(indexes(moved, 20)).toEqual([0, 1])
    expect(titles(moved, 10)).toEqual(['a', 'c'])
    expect(indexes(moved, 10)).toEqual([0, 1])
  })

  it('updates the moved card\'s columnId', () => {
    const moved = moveTaskInBoard(board, 2, 20, 0)
    expect(moved.columns[1].tasks[0].columnId).toBe(20)
  })

  it('clamps an index past the end to "last"', () => {
    const moved = moveTaskInBoard(board, 1, 20, 99)
    expect(titles(moved, 20)).toEqual(['x', 'a'])
    expect(titles(moved, 10)).toEqual(['b', 'c'])
  })

  it('clamps a negative index to the top', () => {
    const moved = moveTaskInBoard(board, 3, 10, -5)
    expect(titles(moved, 10)).toEqual(['c', 'a', 'b'])
  })

  it('moving a card to its own position changes nothing', () => {
    const moved = moveTaskInBoard(board, 2, 10, 1)
    expect(titles(moved, 10)).toEqual(['a', 'b', 'c'])
    expect(indexes(moved, 10)).toEqual([0, 1, 2])
  })

  it('returns the same board for an unknown card or column', () => {
    expect(moveTaskInBoard(board, 999, 10, 0)).toBe(board)
    expect(moveTaskInBoard(board, 1, 999, 0)).toBe(board)
  })

  it('never mutates its input', () => {
    const before = JSON.stringify(board)
    moveTaskInBoard(board, 1, 20, 0)
    expect(JSON.stringify(board)).toBe(before)
  })
})

describe('locateTask', () => {
  it('finds the column and the position', () => {
    expect(locateTask(board, 3)).toEqual({ columnId: 10, index: 2 })
    expect(locateTask(board, 4)).toEqual({ columnId: 20, index: 0 })
  })

  it('returns undefined for an unknown card', () => {
    expect(locateTask(board, 999)).toBeUndefined()
  })
})
