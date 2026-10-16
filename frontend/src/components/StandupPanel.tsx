import { useMutation } from '@tanstack/react-query'
import { aiApi } from '../api/ai'
import type { TaskRef } from '../api/types'
import { AI_NOT_CONFIGURED_HINT, useAiStatus } from '../hooks/useAiStatus'
import { formatDueDate } from '../lib/dueDate'
import { aiErrorMessage, browserTimeZone } from '../lib/errors'

function RefList({ title, refs }: { title: string; refs: TaskRef[] }) {
  if (refs.length === 0) return null
  return (
    <div>
      <h4>{title}</h4>
      <ul>
        {refs.map((ref) => (
          <li key={ref.id}>
            {ref.title} <span className="small">({ref.columnName}, due {formatDueDate(ref.dueDate)})</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// A short briefing of the board for a team meeting. The text is written by the model; the numbers and the
// overdue/due-soon lists next to it are computed by the server, so they are always correct.
export function StandupPanel({ boardId }: { boardId: number }) {
  const status = useAiStatus()
  const standup = useMutation({ mutationFn: () => aiApi.standup(boardId, browserTimeZone()) })
  const notConfigured = status.data?.configured === false

  return (
    <div className="standup">
      <button type="button" className="secondary" onClick={() => standup.mutate()} disabled={standup.isPending || notConfigured}>
        {standup.isPending ? 'Writing summary...' : 'Stand-up summary'}
      </button>
      {notConfigured && <p className="notice">{AI_NOT_CONFIGURED_HINT}</p>}
      {standup.isError && <p className="field-error">{aiErrorMessage(standup.error)}</p>}

      {standup.data && (
        <div className="standup-result">
          <p className="standup-summary">{standup.data.summary}</p>
          <p className="small">
            {standup.data.stats.totalTasks} cards, {standup.data.stats.doneTasks} done,{' '}
            {standup.data.stats.overdue.length} overdue, {standup.data.stats.dueSoon.length} due soon
          </p>
          <RefList title="Overdue" refs={standup.data.stats.overdue} />
          <RefList title="Due soon" refs={standup.data.stats.dueSoon} />
        </div>
      )}
    </div>
  )
}
