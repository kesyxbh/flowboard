import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { tasksApi } from '../api/tasks'
import type { Priority, Task } from '../api/types'
import { errorMessage } from '../lib/errors'
import { SplitSuggestions } from './SplitSuggestions'

// The dialog either creates a card in a given column or edits an existing card.
export type TaskDialogState = { mode: 'create'; columnId: number; columnName: string } | { mode: 'edit'; task: Task }

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH']

// Browser-side validation: the same limits the backend enforces, so obvious mistakes are caught before a request
// is sent (the backend remains the authority and repeats every check).
const schema = z.object({
  title: z.string().trim().min(1, 'Title is required').max(200, 'Title must be at most 200 characters'),
  description: z.string().max(2000, 'Description must be at most 2000 characters'),
  priority: z.enum(['LOW', 'MEDIUM', 'HIGH']),
  dueDate: z.string().regex(/^(\d{4}-\d{2}-\d{2})?$/, 'Please enter a valid date'),
})
type FormValues = z.infer<typeof schema>

interface Props {
  state: TaskDialogState
  boardId: number
  onClose: () => void
}

export function TaskDialog({ state, boardId, onClose }: Props) {
  const queryClient = useQueryClient()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const editing = state.mode === 'edit' ? state.task : null

  // A native <dialog> opened with showModal() gives, for free: a backdrop, focus trapping inside the dialog,
  // and closing with the Escape key.
  useEffect(() => {
    const dialog = dialogRef.current
    if (dialog && !dialog.open) dialog.showModal()
  }, [])

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: editing?.title ?? '',
      description: editing?.description ?? '',
      priority: editing?.priority ?? 'MEDIUM',
      dueDate: editing?.dueDate ?? '',
    },
  })

  function refreshBoardAndClose() {
    void queryClient.invalidateQueries({ queryKey: ['board', boardId] })
    onClose()
  }

  const save = useMutation({
    mutationFn: (values: FormValues) => {
      const description = values.description.trim() === '' ? null : values.description
      const dueDate = values.dueDate === '' ? null : values.dueDate
      if (state.mode === 'create') {
        return tasksApi.create({
          columnId: state.columnId,
          title: values.title,
          description,
          priority: values.priority,
          dueDate,
        })
      }
      // The edit body is a full snapshot: the assignee is sent back unchanged because this form does not edit it.
      return tasksApi.update(state.task.id, {
        title: values.title,
        description,
        assigneeId: state.task.assigneeId,
        priority: values.priority,
        dueDate,
      })
    },
    onSuccess: refreshBoardAndClose,
    onError: (error) => setError('root', { message: errorMessage(error) }),
  })

  const remove = useMutation({
    mutationFn: (taskId: number) => tasksApi.remove(taskId),
    onSuccess: refreshBoardAndClose,
    onError: (error) => setError('root', { message: errorMessage(error) }),
  })

  // Deleting needs a second click on a different button. This is done inside the dialog instead of with
  // window.confirm(): the native box cannot be styled, blocks the whole page, and is hard to test.
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const busy = save.isPending || remove.isPending

  return (
    <dialog
      ref={dialogRef}
      className="dialog"
      onClose={onClose}
      // A click on the dark backdrop (the dialog element itself, outside its content) closes the dialog.
      onClick={(event) => {
        if (event.target === dialogRef.current) dialogRef.current?.close()
      }}
    >
      <div className="dialog-content">
        <h2>{editing ? 'Edit card' : `New card in "${state.mode === 'create' ? state.columnName : ''}"`}</h2>

        <form onSubmit={handleSubmit((values) => save.mutate(values))} noValidate>
          <label className="field">
            <span>Title</span>
            <input autoFocus {...register('title')} maxLength={200} />
            {errors.title && <span className="field-error">{errors.title.message}</span>}
          </label>

          <label className="field">
            <span>Description</span>
            <textarea rows={4} {...register('description')} />
            {errors.description && <span className="field-error">{errors.description.message}</span>}
          </label>

          <div className="field-row">
            <label className="field">
              <span>Priority</span>
              <select {...register('priority')}>
                {PRIORITIES.map((priority) => (
                  <option key={priority} value={priority}>
                    {priority}
                  </option>
                ))}
              </select>
            </label>

            <label className="field">
              <span>Due date</span>
              <input type="date" {...register('dueDate')} />
              {errors.dueDate && <span className="field-error">{errors.dueDate.message}</span>}
            </label>
          </div>

          {errors.root && (
            <p className="field-error" role="alert">
              {errors.root.message}
            </p>
          )}

          <div className="button-row">
            <button type="submit" disabled={busy}>
              {save.isPending ? 'Saving...' : editing ? 'Save' : 'Create card'}
            </button>
            <button type="button" className="secondary" onClick={() => dialogRef.current?.close()} disabled={busy}>
              Cancel
            </button>
            {editing && !confirmingDelete && (
              <button type="button" className="danger" onClick={() => setConfirmingDelete(true)} disabled={busy}>
                Delete
              </button>
            )}
          </div>

          {editing && confirmingDelete && (
            <div className="confirm-delete" role="alert">
              <p>Delete this card for good?</p>
              <div className="button-row">
                <button type="button" className="danger-solid" onClick={() => remove.mutate(editing.id)} disabled={busy}>
                  {remove.isPending ? 'Deleting...' : 'Yes, delete it'}
                </button>
                <button type="button" className="secondary" onClick={() => setConfirmingDelete(false)} disabled={busy}>
                  Keep it
                </button>
              </div>
            </div>
          )}
        </form>

        {editing && <SplitSuggestions task={editing} boardId={boardId} onDone={refreshBoardAndClose} />}
      </div>
    </dialog>
  )
}
