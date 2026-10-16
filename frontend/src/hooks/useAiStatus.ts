import { useQuery } from '@tanstack/react-query'
import { aiApi } from '../api/ai'

/**
 * Whether the server has an LLM API key. The AI buttons use it to explain themselves up front ("AI is not
 * configured") instead of letting the user click and then fail. The answer rarely changes, so it is cached
 * for a minute.
 */
export function useAiStatus() {
  return useQuery({ queryKey: ['ai-status'], queryFn: aiApi.status, staleTime: 60_000 })
}

export const AI_NOT_CONFIGURED_HINT =
  'AI is not configured on the server. Set the LLM_API_KEY environment variable and restart the backend.'
