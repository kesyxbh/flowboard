import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { boardsApi } from '../api/boards'
import { errorMessage } from '../lib/errors'

// The "+ Add column" box at the right end of the board.
export function AddColumnForm({ boardId }: { boardId: number }) {
  const queryClient = useQueryClient()
  const [name, setName] = useState('')

  const add = useMutation({
    mutationFn: (columnName: string) => boardsApi.addColumn(boardId, columnName),
    onSuccess: () => {
      setName('')
      void queryClient.invalidateQueries({ queryKey: ['board', boardId] })
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = name.trim()
    if (trimmed) add.mutate(trimmed)
  }

  return (
    <form className="add-column" onSubmit={submit}>
      <input
        value={name}
        onChange={(event) => setName(event.target.value)}
        placeholder="New column name"
        aria-label="New column name"
        maxLength={100}
      />
      <button type="submit" disabled={add.isPending || name.trim() === ''}>
        Add column
      </button>
      {add.isError && <p className="field-error">{errorMessage(add.error)}</p>}
    </form>
  )
}
