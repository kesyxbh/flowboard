import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { aiApi } from '../api/ai'
import type { QuickAddResponse } from '../api/types'
import { AI_NOT_CONFIGURED_HINT, useAiStatus } from '../hooks/useAiStatus'
import { aiErrorMessage, browserTimeZone } from '../lib/errors'

// "Describe a card in one sentence". The server asks the model to fill in title, priority, due date and column,
// validates the answer, and creates the card. The line under the box shows what was understood, so the user can
// see right away whether the model got it right.
export function QuickAdd({ boardId }: { boardId: number }) {
  const queryClient = useQueryClient()
  const status = useAiStatus()
  const [text, setText] = useState('')
  const [result, setResult] = useState<QuickAddResponse | null>(null)

  const quickAdd = useMutation({
    mutationFn: (sentence: string) => aiApi.quickAdd({ boardId, text: sentence, timeZone: browserTimeZone() }),
    onSuccess: (response) => {
      setResult(response)
      setText('')
      void queryClient.invalidateQueries({ queryKey: ['board', boardId] })
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const sentence = text.trim()
    if (sentence && !quickAdd.isPending) {
      setResult(null)
      quickAdd.mutate(sentence)
    }
  }

  const notConfigured = status.data?.configured === false

  return (
    <div className="quick-add">
      <form onSubmit={submit}>
        <input
          value={text}
          onChange={(event) => setText(event.target.value)}
          placeholder='Quick add with AI, e.g. "write the release notes by Friday, high priority, in Doing"'
          aria-label="Describe a card in one sentence"
          maxLength={500}
          disabled={notConfigured}
        />
        <button type="submit" disabled={quickAdd.isPending || text.trim() === '' || notConfigured}>
          {quickAdd.isPending ? 'Adding...' : 'Add with AI'}
        </button>
      </form>

      {notConfigured && <p className="notice">{AI_NOT_CONFIGURED_HINT}</p>}
      {quickAdd.isError && <p className="field-error">{aiErrorMessage(quickAdd.error)}</p>}
      {result && (
        <div className="quick-add-result" aria-live="polite">
          <p>{result.explanation}</p>
          {result.warnings.map((warning) => (
            <p key={warning} className="warning">
              {warning}
            </p>
          ))}
        </div>
      )}
    </div>
  )
}
