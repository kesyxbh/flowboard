import { getToken } from '../auth/storage'

// Every request goes through this file: one place builds the URL, attaches the token and turns failures into
// one error type. Components never call fetch() themselves.

export class ApiError extends Error {
  status: number // 0 means the server could not be reached at all
  fieldErrors: Record<string, string> // {fieldName: message} for validation failures, otherwise empty

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

// When a request that carried a token is answered with 401 (token expired or invalid), the app must drop the
// session. This module cannot change React state itself, so AuthProvider registers a callback here at start-up.
let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  body?: unknown // sent as JSON
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = getToken()
  const headers: Record<string, string> = {}
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    // fetch itself threw: the server was never reached (backend stopped, offline, ...). That is different from
    // the server answering with an error status.
    throw new ApiError(0, 'Cannot reach the server. Please make sure the backend is running.')
  }

  if (!response.ok) {
    // A 401 on a request that carried a token means the login is no longer valid. The login endpoint's own
    // 401 only means "wrong password", so it does not count. The comparison with getToken() makes sure a slow
    // request from a previous session cannot log out the user who has signed in since.
    if (response.status === 401 && token && token === getToken() && path !== '/auth/login') onUnauthorized?.()

    // Error bodies are RFC 9457 ProblemDetail JSON: {title, status, detail, errors?}. A body can be missing
    // (for example a proxy error page), so parse defensively.
    let message = response.statusText || `Request failed (status ${response.status})`
    let fieldErrors: Record<string, string> = {}
    try {
      const body = await response.json()
      if (typeof body.detail === 'string') message = body.detail
      if (body.errors && typeof body.errors === 'object') fieldErrors = body.errors
    } catch {
      // No JSON body: keep the default message.
    }
    throw new ApiError(response.status, message, fieldErrors)
  }

  // 204 No Content (DELETE) has no body to parse.
  if (response.status === 204) return undefined as T

  // A 200 does not guarantee JSON: with a broken proxy the answer would be an HTML page.
  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    throw new ApiError(response.status, 'The server did not return JSON. Check the proxy setting in vite.config.ts.')
  }
  return response.json() as Promise<T>
}
