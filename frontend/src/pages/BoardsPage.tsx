import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { boardsApi } from '../api/boards'
import { errorMessage } from '../lib/errors'

const DEFAULT_COLUMNS = ['To do', 'Doing', 'Done']

export function BoardsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [withColumns, setWithColumns] = useState(true)

  const boards = useQuery({ queryKey: ['boards'], queryFn: boardsApi.list })

  const create = useMutation({
    mutationFn: async () => {
      const board = await boardsApi.create(name.trim())
      if (withColumns) {
        // One request per column, in order, so the columns get the positions 0, 1, 2.
        for (const columnName of DEFAULT_COLUMNS) {
          await boardsApi.addColumn(board.id, columnName)
        }
      }
      return board
    },
    onSuccess: (board) => {
      void queryClient.invalidateQueries({ queryKey: ['boards'] })
      navigate(`/boards/${board.id}`)
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() && !create.isPending) create.mutate()
  }

  return (
    <div className="boards-page">
      <h1>Your boards</h1>

      <form className="new-board" onSubmit={submit}>
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Name of a new board"
          aria-label="Name of a new board"
          maxLength={100}
        />
        <label className="inline-check">
          <input type="checkbox" checked={withColumns} onChange={(event) => setWithColumns(event.target.checked)} />
          Add To do / Doing / Done columns
        </label>
        <button type="submit" disabled={create.isPending || name.trim() === ''}>
          {create.isPending ? 'Creating...' : 'Create board'}
        </button>
      </form>
      {create.isError && <p className="field-error">{errorMessage(create.error)}</p>}

      {boards.isPending && <p>Loading your boards...</p>}
      {boards.isError && <p className="field-error">{errorMessage(boards.error)}</p>}
      {boards.data && boards.data.length === 0 && <p className="small">You have no boards yet. Create the first one above.</p>}

      <ul className="board-list">
        {boards.data?.map((board) => (
          <li key={board.id}>
            <Link to={`/boards/${board.id}`}>
              <strong>{board.name}</strong>
              <span className="small">Created {new Date(board.createdAt).toLocaleDateString('en-GB')}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}
