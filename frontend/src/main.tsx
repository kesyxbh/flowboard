import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { ErrorBoundary } from './components/ErrorBoundary'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // By default a failed query is retried 3 times (after 1, 2 and 4 seconds), so the user waits 7 seconds to
      // see an error. When the backend answered with a 4xx (404, 400, ...) the request itself is wrong and
      // retrying is pointless. Only "could not connect" (status 0) and 5xx are worth another try, at most twice.
      retry: (failureCount, error) => {
        const worthRetrying = !(error instanceof ApiError) || error.status === 0 || error.status >= 500
        return worthRetrying && failureCount < 2
      },
    },
  },
})

// The nesting order matters: AuthProvider needs the QueryClient and runs inside the router, and every page
// needs AuthProvider.
// The ErrorBoundary is outermost, so it also catches errors thrown by the providers themselves.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ErrorBoundary>
  </StrictMode>,
)
