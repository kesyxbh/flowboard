import { request } from './client'
import type { CreateTaskBody, MoveTaskBody, NewTaskItem, Task, UpdateTaskBody } from './types'

export const tasksApi = {
  create: (body: CreateTaskBody) => request<Task>('/tasks', { method: 'POST', body }),
  /** Several cards at the end of one column, all or nothing. */
  createBulk: (columnId: number, tasks: NewTaskItem[]) =>
    request<Task[]>('/tasks/bulk', { method: 'POST', body: { columnId, tasks } }),
  update: (id: number, body: UpdateTaskBody) => request<Task>(`/tasks/${id}`, { method: 'PATCH', body }),
  move: (id: number, body: MoveTaskBody) => request<Task>(`/tasks/${id}/move`, { method: 'PATCH', body }),
  remove: (id: number) => request<void>(`/tasks/${id}`, { method: 'DELETE' }),
}
