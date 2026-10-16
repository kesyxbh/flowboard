import { ApiError } from '../api/client'

// Turns any error into one sentence for the user.
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    // A 5xx during development usually means "the backend is not running": Vite's proxy answers 500 for it.
    if (error.status >= 500 && error.status !== 502 && error.status !== 503) {
      return 'The server is temporarily unavailable. During development, make sure the backend is running.'
    }
    return error.message
  }
  return 'Something went wrong. Please try again later.'
}

// The AI endpoints answer 503 when no API key is configured and 502 when the model provider failed. Both carry
// a precise explanation from the server, so it is shown as it is. A 422 means the model's answer could not be
// used (for example no title was found), which the server also explains.
export function aiErrorMessage(error: unknown): string {
  if (error instanceof ApiError && [422, 502, 503].includes(error.status)) return error.message
  return errorMessage(error)
}

/** The browser's IANA time zone, such as "Europe/London". Sent to the AI endpoints so "tomorrow" is the user's. */
export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
