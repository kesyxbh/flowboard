import { request } from './client'
import type { BoardDetail, BoardSummary, Column } from './types'

export const boardsApi = {
  list: () => request<BoardSummary[]>('/boards'),
  get: (id: number) => request<BoardDetail>(`/boards/${id}`),
  create: (name: string) => request<BoardSummary>('/boards', { method: 'POST', body: { name } }),
  addColumn: (boardId: number, name: string) =>
    request<Column>(`/boards/${boardId}/columns`, { method: 'POST', body: { name } }),
}
