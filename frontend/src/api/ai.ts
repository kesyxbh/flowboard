import { request } from './client'
import type {
  AiStatus,
  ChatRequest,
  ChatResponse,
  QuickAddRequest,
  QuickAddResponse,
  SplitResponse,
  StandupResponse,
} from './types'

// All AI endpoints require a signed-in user (which also keeps anonymous visitors from spending LLM tokens).
// Without an API key on the server they answer 503.
export const aiApi = {
  status: () => request<AiStatus>('/ai/status'),
  /** The assistant. The server decides whose boards the tools touch: the user is never a parameter. */
  chat: (body: ChatRequest) => request<ChatResponse>('/ai/chat', { method: 'POST', body }),
  /** One sentence in, one card out. */
  quickAdd: (body: QuickAddRequest) => request<QuickAddResponse>('/ai/tasks/quick-add', { method: 'POST', body }),
  /** Suggests sub-tasks for a card. Creates nothing. */
  split: (taskId: number) => request<SplitResponse>(`/ai/tasks/${taskId}/split`, { method: 'POST' }),
  standup: (boardId: number, timeZone: string) =>
    request<StandupResponse>(`/ai/boards/${boardId}/standup`, { method: 'POST', body: { timeZone } }),
}
