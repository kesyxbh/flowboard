import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import type { Column, Task } from '../api/types'
import { columnDndId, taskDndId } from '../lib/dndIds'
import { SortableTaskCard } from './TaskCard'

interface Props {
  column: Column
  today: string
  onOpenTask: (task: Task) => void
  onAddTask: (columnId: number) => void
}

export function ColumnView({ column, today, onOpenTask, onAddTask }: Props) {
  // The whole column is a drop zone. This is what lets a card be dropped into an EMPTY column: there is no
  // other card to drop next to, so without a zone for the column itself there would be nothing to hover over.
  const { setNodeRef, isOver } = useDroppable({ id: columnDndId(column.id) })

  return (
    <section className={`column${isOver ? ' column-over' : ''}`} aria-label={column.name}>
      <header className="column-header">
        <h2>{column.name}</h2>
        <span className="count">{column.tasks.length}</span>
      </header>

      {/* SortableContext needs the ids of the cards in their current order. It computes the "make room"
          animation from that order. */}
      <SortableContext items={column.tasks.map((task) => taskDndId(task.id))} strategy={verticalListSortingStrategy}>
        <div ref={setNodeRef} className="column-cards">
          {column.tasks.map((task) => (
            <SortableTaskCard key={task.id} task={task} today={today} onOpen={onOpenTask} />
          ))}
          {column.tasks.length === 0 && <p className="column-empty">No cards yet</p>}
        </div>
      </SortableContext>

      <button type="button" className="add-card" onClick={() => onAddTask(column.id)}>
        + Add card
      </button>
    </section>
  )
}
