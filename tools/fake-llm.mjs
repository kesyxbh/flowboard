// A SCRIPTED STAND-IN for a language model. It is NOT a model and understands nothing: it recognises which
// FlowBoard feature is calling (by a phrase in the system prompt) and returns a prepared answer in the OpenAI
// "chat completions" format.
//
// Purpose: run the whole AI user interface (quick add, split, stand-up, chat with tool calls) on a machine that
// has no API key - for demos, screenshots and for learning how the pieces connect. Real answers need a real model.
//
// Usage (Node 18+, no dependencies):
//   node tools/fake-llm.mjs                      # listens on http://127.0.0.1:9099
//   then start the backend with
//   LLM_BASE_URL=http://127.0.0.1:9099 LLM_API_KEY=stand-in-key  (PowerShell: $env:LLM_BASE_URL=...; $env:LLM_API_KEY=...)

import http from 'node:http'

const PORT = Number(process.env.PORT ?? 9099)

function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10)
}

function completion(message, finishReason = 'stop') {
  return {
    id: 'chatcmpl-stand-in',
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: 'scripted-stand-in',
    choices: [{ index: 0, message, finish_reason: finishReason }],
    usage: { prompt_tokens: 1, completion_tokens: 1, total_tokens: 2 },
  }
}

function text(content) {
  return completion({ role: 'assistant', content })
}

function toolCall(name, args) {
  return completion(
    {
      role: 'assistant',
      content: null,
      tool_calls: [{ id: 'call_1', type: 'function', function: { name, arguments: JSON.stringify(args) } }],
    },
    'tool_calls',
  )
}

function answer(request) {
  const messages = request.messages ?? []
  const system = String(messages[0]?.content ?? '')
  const last = messages[messages.length - 1] ?? {}
  const lastText = typeof last.content === 'string' ? last.content : JSON.stringify(last.content ?? '')
  const today = /Today is (\d{4}-\d{2}-\d{2})/.exec(system)?.[1] ?? new Date().toISOString().slice(0, 10)

  // --- quick add: one sentence -> a card draft (structured output) ---
  if (system.includes('You turn one sentence into a task card')) {
    // For structured output Spring AI appends "Your response should be in JSON format..." to the user's text.
    // A real model reads past it; this script has to cut it off before looking at the sentence.
    const sentence = lastText.split('Your response should be in JSON format')[0].replace(/^"|"$/g, '').trim()
    const columns = /columns, left to right: \[([^\]]*)\]/.exec(system)?.[1].split(',').map((c) => c.trim()) ?? []
    const wanted = columns.find((c) => sentence.toLowerCase().includes(c.toLowerCase()))
    const title = sentence.replace(/\s*(,|\bby\b|\bdue\b|\bin\b|\bhigh priority\b|\burgent\b|\basap\b|\btomorrow\b).*$/i, '').trim()
    return text(
      JSON.stringify({
        title: title || 'New card',
        description: null,
        priority: /urgent|asap|high/i.test(sentence) ? 'HIGH' : null,
        dueDate: /tomorrow/i.test(sentence) ? addDays(today, 1) : /friday|week/i.test(sentence) ? addDays(today, 3) : null,
        column: wanted ?? null,
      }),
    )
  }

  // --- split: a card -> sub-tasks (structured output) ---
  if (system.includes('You break one card of a kanban board')) {
    const parent = /- title: (.*)/.exec(system)?.[1] ?? 'the card'
    return text(
      JSON.stringify({
        subtasks: [
          { title: `Plan: ${parent}`, description: 'List what has to be done and in which order.' },
          { title: `Do the main work: ${parent}`, description: null },
          { title: `Review and test: ${parent}`, description: 'Check the result against the plan.' },
          { title: `Wrap up: ${parent}`, description: null },
        ],
      }),
    )
  }

  // --- stand-up: board facts -> a short briefing (free text) ---
  if (system.includes('stand-up briefing')) {
    return text(
      'Done\nThe cards in the finished columns are complete.\n\nIn progress\nWork continues in the open columns; ' +
        'high priority cards come first.\n\nRisks\nSee the overdue and due-soon lists next to this text.',
    )
  }

  // --- chat assistant: tool calling ---
  if (last.role === 'tool') {
    return text('Done. Here is what I did: ' + String(last.content).slice(0, 160))
  }
  const boardId = /board ".*?" \(id (\d+)\)/.exec(system)?.[1]
  const firstColumn = /"[^"]*" \(id (\d+)\)/.exec(system.split('Its columns')[1] ?? '')?.[1]
  if (/create|add/i.test(lastText) && firstColumn) {
    return toolCall('createTask', { columnId: Number(firstColumn), title: 'Prepare the demo', priority: 'MEDIUM', dueDate: addDays(today, 2) })
  }
  if (/board|summar/i.test(lastText) && boardId) {
    return toolCall('getBoard', { boardId: Number(boardId) })
  }
  return text('I am a scripted stand-in model. Try: "What is on this board?" or "Create a card to prepare the demo".')
}

const server = http.createServer((req, res) => {
  if (req.method !== 'POST' || !req.url?.endsWith('/chat/completions')) {
    res.writeHead(404).end()
    return
  }
  let body = ''
  req.on('data', (chunk) => (body += chunk))
  req.on('end', () => {
    const response = answer(JSON.parse(body))
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(response))
  })
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Scripted stand-in model listening on http://127.0.0.1:${PORT} (not a real model)`)
})
