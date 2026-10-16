import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  closestCorners,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import { tasksApi } from '../api/tasks'
import type { BoardDetail, Column, Task } from '../api/types'
import { AddColumnForm } from '../components/AddColumnForm'
import { ColumnView } from '../components/ColumnView'
import { QuickAdd } from '../components/QuickAdd'
import { StandupPanel } from '../components/StandupPanel'
import { TaskCardBody } from '../components/TaskCard'
import { TaskDialog, type TaskDialogState } from '../components/TaskDialog'
import { columnDndId, parseDndId, taskDndId } from '../lib/dndIds'
import { toIsoDate } from '../lib/dueDate'
import { findTask, locateTask, moveTaskInBoard } from '../lib/boardMove'
import { errorMessage } from '../lib/errors'

// The board page. The interesting part is drag and drop with an optimistic update:
//
//   while dragging   the cards are rearranged in a local copy (dragBoard) so the user sees where the card will
//                    land. Nothing is sent to the server yet.
//   on drop          the final order is written into the query cache at once (the screen is already correct),
//                    and only then is the move request sent. The old board is kept.
//   on failure       the old board is put back and an error is shown: the UI never keeps a state the server
//                    refused.
//   always           the board is re-fetched afterwards, so what is on screen is what the server stored.
export function BoardPage() {
  const params = useParams()
  const boardId = Number(params.id)
  const queryClient = useQueryClient()
  const boardKey = ['board', boardId] as const

  const boardQuery = useQuery({
    queryKey: boardKey,
    queryFn: () => boardsApi.get(boardId),
    enabled: Number.isInteger(boardId),
  })
  const board = boardQuery.data

  // "Today" is read once when the page opens. It is only used to colour due dates.
  const [today] = useState(() => toIsoDate(new Date()))
  const [dragBoard, setDragBoard] = useState<BoardDetail | null>(null)
  const [activeTaskId, setActiveTaskId] = useState<number | null>(null)
  const [moveError, setMoveError] = useState<string | null>(null)
  const [dialog, setDialog] = useState<TaskDialogState | null>(null)

  // While a card is being dragged the screen shows the local copy, otherwise the server data.
  const shown = dragBoard ?? board

  // PointerSensor with a distance: a drag only starts after the pointer moved 6 px, so a plain click on a card
  // still opens the editor. KeyboardSensor lets people pick a card with Space and move it with the arrow keys.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const moveMutation = useMutation({
    mutationFn: (variables: { taskId: number; targetColumnId: number; orderIndex: number; previous: BoardDetail }) =>
      tasksApi.move(variables.taskId, {
        targetColumnId: variables.targetColumnId,
        orderIndex: variables.orderIndex,
      }),
    onError: (error, variables) => {
      // Roll back: show the board as it was before the drop.
      queryClient.setQueryData(boardKey, variables.previous)
      setMoveError(
        error instanceof ApiError && error.status === 404
          ? 'That card or column no longer exists. The board has been reloaded.'
          : `The card could not be moved: ${errorMessage(error)}`,
      )
    },
    // Whatever happened, ask the server for the truth.
    onSettled: () => queryClient.invalidateQueries({ queryKey: boardKey }),
  })

  function columnOf(source: BoardDetail, dndId: string | number): Column | undefined {
    const parsed = parseDndId(dndId)
    if (!parsed) return undefined
    if (parsed.kind === 'column') return source.columns.find((column) => column.id === parsed.id)
    return source.columns.find((column) => column.tasks.some((task) => task.id === parsed.id))
  }

  function handleDragStart(event: DragStartEvent) {
    const parsed = parseDndId(event.active.id)
    if (!parsed || !board) return
    setMoveError(null)
    setActiveTaskId(parsed.id)
    setDragBoard(board)
  }

  // Fires while the card is hovering over something. When it enters another column the local copy is updated
  // right away, so the cards of that column make room under the pointer.
  function handleDragOver(event: DragOverEvent) {
    const { active, over } = event
    const activeParsed = parseDndId(active.id)
    if (!over || !dragBoard || !activeParsed) return

    const from = columnOf(dragBoard, active.id)
    const to = columnOf(dragBoard, over.id)
    if (!from || !to || from.id === to.id) return

    const overParsed = parseDndId(over.id)
    const index =
      overParsed?.kind === 'task' ? to.tasks.findIndex((task) => task.id === overParsed.id) : to.tasks.length
    setDragBoard(moveTaskInBoard(dragBoard, activeParsed.id, to.id, index))
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    const activeParsed = parseDndId(active.id)
    const original = board
    const working = dragBoard
    setActiveTaskId(null)
    setDragBoard(null)
    if (!activeParsed || !original || !working) return

    // A card dropped on another card in the SAME column is a reorder; dropping into another column was already
    // applied by handleDragOver.
    let finalBoard = working
    const overParsed = over ? parseDndId(over.id) : null
    if (over && overParsed?.kind === 'task') {
      const column = columnOf(working, active.id)
      const overColumn = columnOf(working, over.id)
      if (column && overColumn && column.id === overColumn.id) {
        const newIndex = column.tasks.findIndex((task) => task.id === overParsed.id)
        const oldIndex = column.tasks.findIndex((task) => task.id === activeParsed.id)
        if (newIndex >= 0 && newIndex !== oldIndex) {
          finalBoard = moveTaskInBoard(working, activeParsed.id, column.id, newIndex)
        }
      }
    }

    const before = locateTask(original, activeParsed.id)
    const after = locateTask(finalBoard, activeParsed.id)
    if (!before || !after) return
    if (before.columnId === after.columnId && before.index === after.index) return // dropped where it was

    // Optimistic update: show the result immediately, send the request, roll back if it fails.
    void queryClient.cancelQueries({ queryKey: boardKey }) // an in-flight refetch must not overwrite it
    queryClient.setQueryData(boardKey, finalBoard)
    moveMutation.mutate({
      taskId: activeParsed.id,
      targetColumnId: after.columnId,
      orderIndex: after.index,
      previous: original,
    })
  }

  function handleDragCancel() {
    setActiveTaskId(null)
    setDragBoard(null)
  }

  function openTask(task: Task) {
    setDialog({ mode: 'edit', task })
  }

  function addTask(columnId: number) {
    const column = shown?.columns.find((candidate) => candidate.id === columnId)
    if (column) setDialog({ mode: 'create', columnId, columnName: column.name })
  }

  if (!Number.isInteger(boardId)) return <p className="page-message">This board address is not valid.</p>
  if (boardQuery.isPending) return <p className="page-message">Loading board...</p>
  if (boardQuery.isError || !shown) {
    const notFound = boardQuery.error instanceof ApiError && boardQuery.error.status === 404
    return (
      <div className="page-message">
        <p>{notFound ? 'This board does not exist.' : errorMessage(boardQuery.error)}</p>
        <Link to="/boards">Back to your boards</Link>
      </div>
    )
  }

  const activeTask = activeTaskId === null ? undefined : findTask(shown, activeTaskId)

  return (
    <div className="board-page">
      <div className="board-header">
        <div>
          <Link to="/boards" className="back-link">
            All boards
          </Link>
          <h1>{shown.name}</h1>
        </div>
        <StandupPanel boardId={boardId} />
      </div>

      <QuickAdd boardId={boardId} />

      {moveError && (
        <p className="error-banner" role="alert">
          {moveError}{' '}
          <button type="button" className="link-button" onClick={() => setMoveError(null)}>
            Dismiss
          </button>
        </p>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={handleDragStart}
        onDragOver={handleDragOver}
        onDragEnd={handleDragEnd}
        onDragCancel={handleDragCancel}
      >
        <div className="board-columns">
          {shown.columns.map((column) => (
            <ColumnView key={column.id} column={column} today={today} onOpenTask={openTask} onAddTask={addTask} />
          ))}
          <AddColumnForm boardId={boardId} />
        </div>

        {/* The floating copy that follows the pointer. Rendering it here, outside the columns, means it is not
            clipped by the scrolling column area. */}
        <DragOverlay>{activeTask ? <TaskCardBody task={activeTask} today={today} /> : null}</DragOverlay>
      </DndContext>

      {shown.columns.length === 0 && <p className="page-message">Add your first column to start.</p>}

      {dialog && (
        <TaskDialog
          // A new key makes React create a fresh dialog (and fresh form values) for each card that is opened.
          key={dialog.mode === 'edit' ? taskDndId(dialog.task.id) : columnDndId(dialog.columnId)}
          state={dialog}
          boardId={boardId}
          onClose={() => setDialog(null)}
        />
      )}
    </div>
  )
}
