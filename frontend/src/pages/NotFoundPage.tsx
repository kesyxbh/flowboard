import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="page-message">
      <h1>Page not found</h1>
      <p>
        <Link to="/boards">Go to your boards</Link>
      </p>
    </div>
  )
}
