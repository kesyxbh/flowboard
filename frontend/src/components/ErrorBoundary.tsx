import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  failed: boolean
}

// Without an error boundary, an exception thrown while React renders any component unmounts the whole app and
// the user is left with a blank page. A boundary catches it, shows a message and a way out instead.
//
// It has to be a class component: React has no hook equivalent of componentDidCatch /
// getDerivedStateFromError yet.
export class ErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // In a real product this is where the error would be sent to a monitoring service.
    console.error('Unhandled rendering error', error, info.componentStack)
  }

  render() {
    if (!this.state.failed) return this.props.children
    return (
      <div className="page-message">
        <h1>Something went wrong</h1>
        <p>The page hit an unexpected error. Your data on the server is not affected.</p>
        <button type="button" onClick={() => window.location.reload()}>
          Reload the page
        </button>
      </div>
    )
  }
}
