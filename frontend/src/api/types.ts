// The shapes of the JSON exchanged with the backend. Keep them in sync with the record classes in
// backend/src/main/java/com/flowboard/web/dto. Dates are plain strings: "2026-10-14" for a date and an ISO
// instant for timestamps; nothing here depends on the browser's time zone.

export type Priority = 'LOW' | 'MEDIUM' | 'HIGH'

export interface User {
  id: number
  username: string
  email: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface BoardSummary {
  id: number
  name: string
  createdAt: string
}

export interface Task {
  id: number
  columnId: number
  title: string
  description: string | null
  assigneeId: number | null
  priority: Priority
  dueDate: string | null // "2026-10-14"
  orderIndex: number // position inside its column, 0 = top
}

export interface Column {
  id: number
  boardId: number
  name: string
  orderIndex: number // position on the board, 0 = leftmost
  tasks: Task[] // already sorted top to bottom
}

export interface BoardDetail {
  id: number
  name: string
  columns: Column[] // already sorted left to right
}

export interface CreateTaskBody {
  columnId: number
  title: string
  description?: string | null
  assigneeId?: number | null
  priority?: Priority
  dueDate?: string | null
}

/** A complete snapshot of the editable fields: null clears a field, it does not mean "unchanged". */
export interface UpdateTaskBody {
  title: string
  description: string | null
  assigneeId: number | null
  priority: Priority
  dueDate: string | null
}

export interface MoveTaskBody {
  targetColumnId: number
  orderIndex: number // final position in the target column (0 = top); past the end means "last"
}

export interface NewTaskItem {
  title: string
  description?: string | null
  priority?: Priority
  dueDate?: string | null
}

// ---- AI endpoints ----

export interface AiStatus {
  configured: boolean
  model: string
}

export interface ChatRequest {
  /** Omit it to start a conversation; send back the id from the previous response afterwards. */
  conversationId?: string
  message: string
  boardId?: number
  timeZone?: string
}

/** One tool the assistant invoked while producing its reply. */
export interface ToolCall {
  name: string
  args: Record<string, unknown> // the arguments the model supplied, without the ones it left out
  ok: boolean // false when the tool refused or failed
}

export interface ChatResponse {
  conversationId: string
  reply: string
  toolCalls: ToolCall[]
}

export interface QuickAddRequest {
  boardId: number
  text: string
  timeZone?: string
}

export interface QuickAddResponse {
  task: Task
  columnName: string
  explanation: string
  warnings: string[]
}

export interface SubtaskSuggestion {
  title: string
  description: string | null
}

/** A preview: nothing has been created yet. */
export interface SplitResponse {
  taskId: number
  columnId: number
  subtasks: SubtaskSuggestion[]
  explanation: string
}

export interface TaskRef {
  id: number
  title: string
  dueDate: string
  columnName: string
}

export interface StandupResponse {
  summary: string
  stats: {
    totalTasks: number
    doneTasks: number
    overdue: TaskRef[]
    dueSoon: TaskRef[]
  }
}
