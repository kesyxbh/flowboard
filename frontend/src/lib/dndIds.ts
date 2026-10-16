// dnd-kit identifies every draggable and every drop zone with one id. Cards and columns live in the same id
// space, so the ids carry a prefix to tell them apart: "task-12" is the card with id 12, "column-3" is the drop
// zone of the column with id 3.

export type DndKind = 'task' | 'column'

export function taskDndId(taskId: number): string {
  return `task-${taskId}`
}

export function columnDndId(columnId: number): string {
  return `column-${columnId}`
}

export function parseDndId(id: string | number): { kind: DndKind; id: number } | null {
  const match = /^(task|column)-(\d+)$/.exec(String(id))
  if (!match) return null
  return { kind: match[1] as DndKind, id: Number(match[2]) }
}
