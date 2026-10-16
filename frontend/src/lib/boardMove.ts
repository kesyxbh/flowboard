import type { BoardDetail, Column, Task } from '../api/types'

// The client-side twin of TaskService.moveTask in the backend. When the user drops a card, the board on screen
// must change immediately (an "optimistic update") without waiting for the server. To get exactly what the
// server will compute, this function follows the same algorithm:
//
//   1. take the target column's cards and remove the moving card from that list (it is in it when the move
//      stays inside one column),
//   2. insert the card at `index` - an index past the end means "last",
//   3. renumber the target column 0..n-1,
//   4. if the card changed column, renumber the source column too, so it has no gap.
//
// It is a pure function: it never changes its input and returns a new board. That makes it trivial to test, and
// it lets the caller keep the old board for a rollback.

export function moveTaskInBoard(board: BoardDetail, taskId: number, targetColumnId: number, index: number): BoardDetail {
  const moving = findTask(board, taskId)
  const targetExists = board.columns.some((column) => column.id === targetColumnId)
  if (!moving || !targetExists) return board // unknown card or column: nothing to do

  const columns = board.columns.map((column): Column => {
    const withoutMoving = column.tasks.filter((task) => task.id !== taskId)
    if (column.id !== targetColumnId) {
      return { ...column, tasks: renumber(withoutMoving) }
    }
    const position = Math.max(0, Math.min(index, withoutMoving.length))
    const tasks = [...withoutMoving]
    tasks.splice(position, 0, { ...moving, columnId: targetColumnId })
    return { ...column, tasks: renumber(tasks) }
  })
  return { ...board, columns }
}

function renumber(tasks: Task[]): Task[] {
  return tasks.map((task, position) => (task.orderIndex === position ? task : { ...task, orderIndex: position }))
}

export function findTask(board: BoardDetail, taskId: number): Task | undefined {
  for (const column of board.columns) {
    const task = column.tasks.find((candidate) => candidate.id === taskId)
    if (task) return task
  }
  return undefined
}

/** Where a card currently is: its column and its position inside that column. */
export function locateTask(board: BoardDetail, taskId: number): { columnId: number; index: number } | undefined {
  for (const column of board.columns) {
    const index = column.tasks.findIndex((task) => task.id === taskId)
    if (index >= 0) return { columnId: column.id, index }
  }
  return undefined
}
