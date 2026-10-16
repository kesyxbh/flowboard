import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { aiApi } from '../api/ai'
import { tasksApi } from '../api/tasks'
import type { SplitResponse, Task } from '../api/types'
import { AI_NOT_CONFIGURED_HINT, useAiStatus } from '../hooks/useAiStatus'
import { aiErrorMessage, errorMessage } from '../lib/errors'

interface Props {
  task: Task
  boardId: number
  onDone: () => void
}

// "Split with AI": a two-step flow with the user in the middle.
//   1. Ask the server for suggestions. This creates nothing; it only returns a preview.
//   2. The user ticks the ones they want, and only then are cards created (one bulk request, all or nothing).
export function SplitSuggestions({ task, boardId, onDone }: Props) {
  const queryClient = useQueryClient()
  const status = useAiStatus()
  const [preview, setPreview] = useState<SplitResponse | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const suggest = useMutation({
    mutationFn: () => aiApi.split(task.id),
    onSuccess: (response) => {
      setPreview(response)
      setSelected(new Set(response.subtasks.map((_, index) => index))) // everything ticked by default
    },
  })

  const create = useMutation({
    mutationFn: () => {
      const chosen = (preview?.subtasks ?? []).filter((_, index) => selected.has(index))
      // The sub-tasks inherit the priority of the card they were split from.
      return tasksApi.createBulk(
        task.columnId,
        chosen.map((item) => ({ title: item.title, description: item.description, priority: task.priority })),
      )
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['board', boardId] })
      onDone()
    },
  })

  function toggle(index: number) {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(index)) next.delete(index)
      else next.add(index)
      return next
    })
  }

  const notConfigured = status.data?.configured === false

  return (
    <section className="split-box">
      <h3>Split with AI</h3>

      {!preview && (
        <>
          <p className="small">Ask the assistant to break this card into smaller cards. Nothing is created until you confirm.</p>
          <button type="button" onClick={() => suggest.mutate()} disabled={suggest.isPending || notConfigured}>
            {suggest.isPending ? 'Thinking...' : 'Suggest sub-tasks'}
          </button>
          {notConfigured && <p className="notice">{AI_NOT_CONFIGURED_HINT}</p>}
          {suggest.isError && <p className="field-error">{aiErrorMessage(suggest.error)}</p>}
        </>
      )}

      {preview && (
        <>
          <p className="small">{preview.explanation}</p>
          <ul className="suggestions">
            {preview.subtasks.map((item, index) => (
              <li key={index}>
                <label>
                  <input type="checkbox" checked={selected.has(index)} onChange={() => toggle(index)} />
                  <span>
                    <strong>{item.title}</strong>
                    {item.description && <em> - {item.description}</em>}
                  </span>
                </label>
              </li>
            ))}
          </ul>
          <div className="button-row">
            <button type="button" onClick={() => create.mutate()} disabled={create.isPending || selected.size === 0}>
              {create.isPending ? 'Adding...' : `Add ${selected.size} card${selected.size === 1 ? '' : 's'}`}
            </button>
            <button type="button" className="secondary" onClick={() => setPreview(null)} disabled={create.isPending}>
              Discard
            </button>
          </div>
          {create.isError && <p className="field-error">{errorMessage(create.error)}</p>}
        </>
      )}
    </section>
  )
}
