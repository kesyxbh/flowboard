import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useMatch } from 'react-router-dom'
import { aiApi } from '../api/ai'
import type { ToolCall } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { useAiStatus } from '../hooks/useAiStatus'
import { aiErrorMessage, browserTimeZone } from '../lib/errors'

interface ChatMessage {
  role: 'user' | 'assistant' | 'error'
  text: string
  toolCalls?: ToolCall[]
}

const SUGGESTIONS = ['What is on this board?', 'Summarise this board', 'Create a card to prepare the demo']

// {columnId: 3, title: "Demo"} -> "columnId=3, title=Demo" (shown as a tooltip on the badge)
function summariseArgs(args: Record<string, unknown> | undefined): string {
  return Object.entries(args ?? {})
    .map(([key, value]) => `${key}=${String(value)}`)
    .join(', ')
}

// The floating assistant. It is mounted once in the Layout and re-created when the user changes (see the `key`
// there), so a new login never inherits the previous conversation. When the user is looking at a board, that
// board's id is sent with every message so "add a card" needs no board name.
export function ChatWidget() {
  const { session } = useAuth()
  const location = useLocation()
  const queryClient = useQueryClient()
  const status = useAiStatus()
  const boardMatch = useMatch('/boards/:id')
  const boardId = boardMatch?.params.id ? Number(boardMatch.params.id) : undefined

  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [draft, setDraft] = useState('')
  // Issued by the server with the first reply and sent back with every later message.
  const [conversationId, setConversationId] = useState<string | undefined>(undefined)
  const endRef = useRef<HTMLDivElement>(null)

  const send = useMutation({
    mutationFn: (message: string) => aiApi.chat({ conversationId, message, boardId, timeZone: browserTimeZone() }),
    onSuccess: (data) => {
      const toolCalls = data.toolCalls ?? []
      setConversationId(data.conversationId)
      setMessages((current) => [...current, { role: 'assistant', text: data.reply, toolCalls }])
      // If the assistant changed anything, what the user is looking at is stale.
      if (toolCalls.some((call) => call.ok)) {
        void queryClient.invalidateQueries({ queryKey: ['board'] })
        void queryClient.invalidateQueries({ queryKey: ['boards'] })
      }
    },
    onError: (error) => {
      setMessages((current) => [...current, { role: 'error', text: aiErrorMessage(error) }])
    },
  })

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' })
  }, [messages, send.isPending, open])

  function submit(text: string) {
    const message = text.trim()
    if (!message || send.isPending) return
    setMessages((current) => [...current, { role: 'user', text: message }])
    setDraft('')
    send.mutate(message)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submit(draft)
  }

  if (!session) return null

  if (!open) {
    return (
      <button type="button" className="chat-launcher" onClick={() => setOpen(true)}>
        Ask the assistant
      </button>
    )
  }

  return (
    <aside className="chat-panel" aria-label="Board assistant">
      <header className="chat-header">
        <strong>Board assistant</strong>
        <button type="button" onClick={() => setOpen(false)} aria-label="Close the assistant">
          x
        </button>
      </header>

      <div className="chat-body" aria-live="polite">
        {status.data?.configured === false && (
          <p className="notice">
            AI is not configured on the server. Set the <code>LLM_API_KEY</code> environment variable and restart the
            backend.
          </p>
        )}

        {messages.length === 0 && (
          <div>
            <p className="small">
              {boardId
                ? 'I can read this board and create cards for you. Try:'
                : 'Open a board and I can read it and create cards for you. Try:'}
            </p>
            <div className="chat-suggestions">
              {SUGGESTIONS.map((suggestion) => (
                <button key={suggestion} type="button" onClick={() => submit(suggestion)} disabled={send.isPending}>
                  {suggestion}
                </button>
              ))}
            </div>
            {!boardId && (
              <p className="small">
                <Link to="/boards" state={{ from: location }}>
                  Go to your boards
                </Link>
              </p>
            )}
          </div>
        )}

        {messages.map((message, index) => (
          <div key={index} className={`chat-message chat-${message.role}`}>
            <p>{message.text}</p>
            {message.toolCalls && message.toolCalls.length > 0 && (
              <p className="chat-tools">
                {message.toolCalls.map((call, i) => (
                  <span
                    key={i}
                    className={call.ok ? 'badge badge-ok' : 'badge badge-warn'}
                    title={summariseArgs(call.args)}
                  >
                    {call.name} {call.ok ? 'ok' : 'failed'}
                  </span>
                ))}
              </p>
            )}
          </div>
        ))}

        {send.isPending && <p className="chat-message chat-assistant small">Thinking...</p>}
        <div ref={endRef} />
      </div>

      <form className="chat-form" onSubmit={handleSubmit}>
        <input
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          placeholder="Type a message..."
          aria-label="Message"
          maxLength={1000}
        />
        <button type="submit" disabled={send.isPending || draft.trim() === ''}>
          Send
        </button>
      </form>
    </aside>
  )
}
