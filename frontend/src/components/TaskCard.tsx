import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { Task } from '../api/types'
import { dueStatus, formatDueDate } from '../lib/dueDate'
import { taskDndId } from '../lib/dndIds'

interface BodyProps {
  task: Task
  today: string // "yyyy-MM-dd" in the browser's time zone, decides whether a due date is overdue
}

// What a card looks like. It is a separate component because it is drawn twice: inside the column (wrapped by
// SortableTaskCard) and as the floating copy that follows the pointer while dragging (DragOverlay).
export function TaskCardBody({ task, today }: BodyProps) {
  const status = dueStatus(task.dueDate, today)
  return (
    <div className={`task-card priority-${task.priority.toLowerCase()}`}>
      <p className="task-title">{task.title}</p>
      <div className="task-meta">
        <span className={`badge badge-${task.priority.toLowerCase()}`}>{task.priority}</span>
        {task.dueDate && (
          <span className={`due due-${status}`} title={status === 'overdue' ? 'Overdue' : undefined}>
            {status === 'overdue' ? 'Overdue - ' : status === 'today' ? 'Today - ' : ''}
            {formatDueDate(task.dueDate)}
          </span>
        )}
      </div>
    </div>
  )
}

interface SortableProps extends BodyProps {
  onOpen: (task: Task) => void
}

// A card that can be dragged and that other cards can be dropped next to. useSortable returns:
//   setNodeRef  - attach to the element so dnd-kit can measure it
//   attributes  - accessibility attributes (role, tabIndex, aria-*), which also make it keyboard-draggable
//   listeners   - the pointer/keyboard handlers that start a drag
//   transform   - how far dnd-kit wants the card moved right now (the "make room" animation)
export function SortableTaskCard({ task, today, onOpen }: SortableProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: taskDndId(task.id),
  })

  return (
    <div
      ref={setNodeRef}
      style={{
        transform: CSS.Transform.toString(transform),
        transition,
        // The original card stays in place as a faded placeholder while its copy follows the pointer.
        opacity: isDragging ? 0.35 : 1,
      }}
      {...attributes}
      {...listeners}
      // A click opens the editor. A real drag never produces a click because the pointer sensor only starts a
      // drag after the pointer has moved a few pixels (see BoardPage).
      onClick={() => onOpen(task)}
    >
      <TaskCardBody task={task} today={today} />
    </div>
  )
}
